package com.czcode.authservice.auth.controller;

import com.czcode.authservice.auth.dto.AuthTokenResponse;
import com.czcode.authservice.auth.dto.LoginRequest;
import com.czcode.authservice.auth.dto.LogoutRequest;
import com.czcode.authservice.auth.dto.MeResponse;
import com.czcode.authservice.auth.dto.RefreshTokenRequest;
import com.czcode.authservice.auth.dto.RegisterRequest;
import com.czcode.authservice.auth.dto.SendCodeRequest;
import com.czcode.authservice.auth.dto.SendCodeResponse;
import com.czcode.authservice.auth.service.AuthService;
import com.czcode.authservice.security.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/send-code")
  public SendCodeResponse sendCode(@Valid @RequestBody SendCodeRequest request) {
    return authService.sendRegisterCode(request);
  }

  @PostMapping("/register")
  public AuthTokenResponse register(
      @Valid @RequestBody RegisterRequest request,
      HttpServletRequest httpServletRequest) {
    return authService.register(request, clientIp(httpServletRequest), userAgent(httpServletRequest));
  }

  @PostMapping("/login")
  public AuthTokenResponse login(
      @Valid @RequestBody LoginRequest request,
      HttpServletRequest httpServletRequest) {
    return authService.login(request, clientIp(httpServletRequest), userAgent(httpServletRequest));
  }

  @PostMapping("/refresh")
  public AuthTokenResponse refresh(
      @Valid @RequestBody RefreshTokenRequest request,
      HttpServletRequest httpServletRequest) {
    return authService.refresh(request, clientIp(httpServletRequest), userAgent(httpServletRequest));
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @AuthenticationPrincipal AuthPrincipal principal,
      @Valid @RequestBody LogoutRequest request) {
    authService.logout(requireAccountId(principal), request);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/logout-all")
  public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal AuthPrincipal principal) {
    authService.logoutAll(requireAccountId(principal));
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/me")
  public MeResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
    if (principal == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized");
    }
    return authService.me(principal);
  }

  private String clientIp(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      return forwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }

  private String userAgent(HttpServletRequest request) {
    return request.getHeader("User-Agent");
  }

  private UUID requireAccountId(AuthPrincipal principal) {
    if (principal == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized");
    }
    return principal.accountId();
  }
}
