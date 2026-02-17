package com.czcode.authservice.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.czcode.authservice.auth.dto.AuthTokenResponse;
import com.czcode.authservice.auth.dto.LoginRequest;
import com.czcode.authservice.auth.dto.LogoutRequest;
import com.czcode.authservice.auth.dto.RefreshTokenRequest;
import com.czcode.authservice.auth.entity.AuthAccountEntity;
import com.czcode.authservice.auth.entity.AuthRefreshTokenEntity;
import com.czcode.authservice.auth.repository.AuthAccountRepository;
import com.czcode.authservice.auth.repository.AuthRefreshTokenRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthSecurityIntegrationTests {

  private static final short STATUS_ACTIVE = 1;
  private static final String TEST_PASSWORD = "Password123";
  private static final String V1_SECRET = "test-secret-v1-at-least-32-bytes";

  @Autowired
  private AuthService authService;

  @Autowired
  private JwtService jwtService;

  @Autowired
  private AuthAccountRepository authAccountRepository;

  @Autowired
  private AuthRefreshTokenRepository authRefreshTokenRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Test
  void logoutShouldRevokeRefreshToken() {
    AuthAccountEntity account = createAccount("logout@example.com");

    AuthTokenResponse token = authService.login(
        new LoginRequest(account.getEmail(), TEST_PASSWORD, "web", "chrome"),
        "127.0.0.1",
        "JUnit");

    authService.logout(account.getId(), new LogoutRequest(token.refreshToken()));

    ResponseStatusException ex = assertThrows(
        ResponseStatusException.class,
        () -> authService.refresh(
            new RefreshTokenRequest(token.refreshToken(), "web", "chrome"),
            "127.0.0.1",
            "JUnit"));
    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void logoutAllShouldRevokeAllRefreshTokens() {
    AuthAccountEntity account = createAccount("logout-all@example.com");

    authService.login(
        new LoginRequest(account.getEmail(), TEST_PASSWORD, "web", "chrome"),
        "127.0.0.1",
        "JUnit");
    authService.login(
        new LoginRequest(account.getEmail(), TEST_PASSWORD, "mobile", "ios"),
        "127.0.0.1",
        "JUnit");

    int revokedCount = authService.logoutAll(account.getId());

    List<AuthRefreshTokenEntity> tokens = authRefreshTokenRepository.findByAccountIdOrderByCreatedAtDesc(account.getId());
    long activeCount = authRefreshTokenRepository.countByAccountIdAndRevokedAtIsNull(account.getId());
    assertThat(revokedCount).isGreaterThanOrEqualTo(2);
    assertThat(tokens).hasSizeGreaterThanOrEqualTo(2);
    assertThat(activeCount).isZero();
  }

  @Test
  void jwtShouldIncludeKidAndAcceptLegacyTokenWithoutKid() {
    UUID accountId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    String email = "kid@example.com";

    String tokenWithKid = jwtService.generateAccessToken(accountId, userId, email);
    String headerJson = decodeHeader(tokenWithKid);
    assertThat(headerJson).contains("\"kid\":\"v2\"");

    Claims claims = jwtService.parseAndValidate(tokenWithKid);
    assertThat(claims.getSubject()).isEqualTo(accountId.toString());
    assertThat(claims.get("user_id", String.class)).isEqualTo(userId.toString());

    SecretKey v1Key = Keys.hmacShaKeyFor(V1_SECRET.getBytes(StandardCharsets.UTF_8));
    String legacyToken = Jwts.builder()
        .subject(accountId.toString())
        .claim("user_id", userId.toString())
        .claim("email", email)
        .issuedAt(java.util.Date.from(Instant.now()))
        .expiration(java.util.Date.from(Instant.now().plusSeconds(600)))
        .signWith(v1Key, Jwts.SIG.HS256)
        .compact();

    Claims legacyClaims = jwtService.parseAndValidate(legacyToken);
    assertThat(legacyClaims.getSubject()).isEqualTo(accountId.toString());
    assertThat(legacyClaims.get("user_id", String.class)).isEqualTo(userId.toString());
  }

  private AuthAccountEntity createAccount(String email) {
    AuthAccountEntity account = new AuthAccountEntity();
    account.setUserId(UUID.randomUUID());
    account.setLoginName(email);
    account.setEmail(email);
    account.setPasswordHash(passwordEncoder.encode(TEST_PASSWORD));
    account.setPasswordAlgo("bcrypt");
    account.setStatus(STATUS_ACTIVE);
    account.setFailedLoginCount(0);
    return authAccountRepository.save(account);
  }

  private String decodeHeader(String token) {
    String[] parts = token.split("\\.");
    assertThat(parts.length).isGreaterThanOrEqualTo(2);
    return new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
  }
}
