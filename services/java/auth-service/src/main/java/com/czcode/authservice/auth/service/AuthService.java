package com.czcode.authservice.auth.service;

import com.czcode.authservice.auth.dto.AuthTokenResponse;
import com.czcode.authservice.auth.dto.LoginRequest;
import com.czcode.authservice.auth.dto.LogoutRequest;
import com.czcode.authservice.auth.dto.MeResponse;
import com.czcode.authservice.auth.dto.RefreshTokenRequest;
import com.czcode.authservice.auth.dto.RegisterRequest;
import com.czcode.authservice.auth.dto.SendCodeRequest;
import com.czcode.authservice.auth.dto.SendCodeResponse;
import com.czcode.authservice.auth.entity.AuthAccountEntity;
import com.czcode.authservice.auth.entity.AuthEmailCodeEntity;
import com.czcode.authservice.auth.entity.AuthRefreshTokenEntity;
import com.czcode.authservice.auth.repository.AuthAccountRepository;
import com.czcode.authservice.auth.repository.AuthEmailCodeRepository;
import com.czcode.authservice.auth.repository.AuthRefreshTokenRepository;
import com.czcode.authservice.security.AuthPrincipal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

  private static final short STATUS_ACTIVE = 1;
  private static final short STATUS_LOCKED = 2;
  private static final short STATUS_DISABLED = 3;
  private static final String PURPOSE_REGISTER = "register";
  private static final Duration LOGIN_LOCK_DURATION = Duration.ofMinutes(15);
  private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;

  private final AuthAccountRepository authAccountRepository;
  private final AuthEmailCodeRepository authEmailCodeRepository;
  private final AuthRefreshTokenRepository authRefreshTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final UserServiceClient userServiceClient;
  private final long codeTtlSeconds;
  private final long refreshTokenTtlSeconds;
  private final boolean debugReturnCode;

  public AuthService(
      AuthAccountRepository authAccountRepository,
      AuthEmailCodeRepository authEmailCodeRepository,
      AuthRefreshTokenRepository authRefreshTokenRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      UserServiceClient userServiceClient,
      @Value("${auth.code.ttl-seconds:300}") long codeTtlSeconds,
      @Value("${auth.jwt.refresh-token-ttl-seconds:1209600}") long refreshTokenTtlSeconds,
      @Value("${auth.code.debug-return-enabled:true}") boolean debugReturnCode) {
    this.authAccountRepository = authAccountRepository;
    this.authEmailCodeRepository = authEmailCodeRepository;
    this.authRefreshTokenRepository = authRefreshTokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.userServiceClient = userServiceClient;
    this.codeTtlSeconds = codeTtlSeconds;
    this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    this.debugReturnCode = debugReturnCode;
  }

  @Transactional
  public SendCodeResponse sendRegisterCode(SendCodeRequest request) {
    String email = normalizeEmail(request.email());
    Instant now = Instant.now();

    authEmailCodeRepository.findTopByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
            email,
            PURPOSE_REGISTER)
        .ifPresent(existing -> {
          if (existing.getCreatedAt() != null && existing.getCreatedAt().isAfter(now.minusSeconds(60))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "please retry after 60 seconds");
          }
        });

    String code = generateSixDigitCode();
    AuthEmailCodeEntity entity = new AuthEmailCodeEntity();
    entity.setEmail(email);
    entity.setPurpose(PURPOSE_REGISTER);
    entity.setCodeHash(passwordEncoder.encode(code));
    entity.setExpiresAt(now.plusSeconds(codeTtlSeconds));
    entity.setConsumedAt(null);
    entity.setSendCount(1);
    authEmailCodeRepository.save(entity);

    return new SendCodeResponse(
        "verification code sent",
        entity.getExpiresAt(),
        debugReturnCode ? code : null);
  }

  @Transactional
  public AuthTokenResponse register(RegisterRequest request, String ip, String userAgent) {
    String email = normalizeEmail(request.email());
    Instant now = Instant.now();

    if (authAccountRepository.existsByEmail(email)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "email is already registered");
    }

    AuthEmailCodeEntity latestCode = authEmailCodeRepository
        .findTopByEmailAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(email, PURPOSE_REGISTER)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "verification code not found"));

    if (latestCode.getExpiresAt().isBefore(now)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "verification code expired");
    }
    if (!passwordEncoder.matches(request.code(), latestCode.getCodeHash())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "verification code invalid");
    }
    latestCode.setConsumedAt(now);
    authEmailCodeRepository.save(latestCode);

    UUID userId = userServiceClient.createDefaultUserProfile(email, request.nickname());

    AuthAccountEntity account = new AuthAccountEntity();
    account.setUserId(userId);
    account.setLoginName(email);
    account.setEmail(email);
    account.setPasswordHash(passwordEncoder.encode(request.password()));
    account.setPasswordAlgo("bcrypt");
    account.setStatus(STATUS_ACTIVE);
    account.setFailedLoginCount(0);
    AuthAccountEntity saved = authAccountRepository.save(account);

    return issueTokens(saved, null, null, ip, userAgent);
  }

  @Transactional
  public AuthTokenResponse login(LoginRequest request, String ip, String userAgent) {
    String email = normalizeEmail(request.email());
    Instant now = Instant.now();

    AuthAccountEntity account = authAccountRepository.findByEmail(email)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials"));

    ensureAccountCanLogin(account, now);

    if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
      int failed = account.getFailedLoginCount() + 1;
      account.setFailedLoginCount(failed);
      if (failed >= MAX_FAILED_LOGIN_ATTEMPTS) {
        account.setStatus(STATUS_LOCKED);
        account.setLockedUntil(now.plus(LOGIN_LOCK_DURATION));
      }
      authAccountRepository.save(account);
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials");
    }

    account.setFailedLoginCount(0);
    account.setLockedUntil(null);
    account.setStatus(STATUS_ACTIVE);
    account.setLastLoginAt(now);
    AuthAccountEntity saved = authAccountRepository.save(account);
    return issueTokens(saved, request.clientId(), request.deviceInfo(), ip, userAgent);
  }

  @Transactional
  public AuthTokenResponse refresh(RefreshTokenRequest request, String ip, String userAgent) {
    Instant now = Instant.now();
    String tokenHash = sha256Hex(request.refreshToken());

    AuthRefreshTokenEntity refreshTokenEntity = authRefreshTokenRepository
        .findByTokenHashAndRevokedAtIsNull(tokenHash)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid refresh token"));

    if (refreshTokenEntity.getExpiresAt().isBefore(now)) {
      refreshTokenEntity.setRevokedAt(now);
      authRefreshTokenRepository.save(refreshTokenEntity);
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "refresh token expired");
    }

    AuthAccountEntity account = authAccountRepository.findById(refreshTokenEntity.getAccountId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "account not found"));

    ensureAccountCanLogin(account, now);

    refreshTokenEntity.setRevokedAt(now);
    authRefreshTokenRepository.save(refreshTokenEntity);

    return issueTokens(account, request.clientId(), request.deviceInfo(), ip, userAgent);
  }

  @Transactional
  public void logout(UUID accountId, LogoutRequest request) {
    String tokenHash = sha256Hex(request.refreshToken());
    AuthRefreshTokenEntity tokenEntity = authRefreshTokenRepository
        .findByAccountIdAndTokenHashAndRevokedAtIsNull(accountId, tokenHash)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid refresh token"));

    tokenEntity.setRevokedAt(Instant.now());
    authRefreshTokenRepository.save(tokenEntity);
  }

  @Transactional
  public int logoutAll(UUID accountId) {
    return authRefreshTokenRepository.revokeAllActiveByAccountId(accountId, Instant.now());
  }

  @Transactional(readOnly = true)
  public MeResponse me(AuthPrincipal principal) {
    AuthAccountEntity account = authAccountRepository.findById(principal.accountId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "account not found"));

    return new MeResponse(account.getId(), account.getUserId(), account.getEmail(), account.getStatus());
  }

  private AuthTokenResponse issueTokens(
      AuthAccountEntity account,
      String clientId,
      String deviceInfo,
      String ip,
      String userAgent) {
    Instant now = Instant.now();
    String accessToken = jwtService.generateAccessToken(account.getId(), account.getUserId(), account.getEmail());
    Instant accessTokenExpiresAt = jwtService.accessTokenExpiresAt(now);

    String refreshToken = generateRefreshToken();
    AuthRefreshTokenEntity tokenEntity = new AuthRefreshTokenEntity();
    tokenEntity.setAccountId(account.getId());
    tokenEntity.setTokenHash(sha256Hex(refreshToken));
    tokenEntity.setClientId(clientId);
    tokenEntity.setDeviceInfo(deviceInfo);
    tokenEntity.setIp(ip);
    tokenEntity.setUserAgent(userAgent);
    tokenEntity.setExpiresAt(now.plusSeconds(refreshTokenTtlSeconds));
    authRefreshTokenRepository.save(tokenEntity);

    return new AuthTokenResponse(
        "Bearer",
        accessToken,
        accessTokenExpiresAt,
        refreshToken,
        account.getId(),
        account.getUserId(),
        account.getEmail());
  }

  private void ensureAccountCanLogin(AuthAccountEntity account, Instant now) {
    if (account.getStatus() == STATUS_DISABLED) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "account disabled");
    }
    if (account.getStatus() == STATUS_LOCKED) {
      if (account.getLockedUntil() != null && account.getLockedUntil().isAfter(now)) {
        throw new ResponseStatusException(HttpStatus.LOCKED, "account is locked");
      }
      account.setStatus(STATUS_ACTIVE);
      account.setLockedUntil(null);
      account.setFailedLoginCount(0);
    }
  }

  private String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private String generateSixDigitCode() {
    int code = ThreadLocalRandom.current().nextInt(100000, 1_000_000);
    return Integer.toString(code);
  }

  private String generateRefreshToken() {
    byte[] random = new byte[32];
    ThreadLocalRandom.current().nextBytes(random);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
  }

  private String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder builder = new StringBuilder(hash.length * 2);
      for (byte b : hash) {
        builder.append(String.format("%02x", b));
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 not available", ex);
    }
  }
}
