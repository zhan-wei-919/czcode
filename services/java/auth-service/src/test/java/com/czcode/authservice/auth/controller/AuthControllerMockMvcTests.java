package com.czcode.authservice.auth.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.czcode.authservice.auth.dto.AuthTokenResponse;
import com.czcode.authservice.auth.dto.LoginRequest;
import com.czcode.authservice.auth.entity.AuthAccountEntity;
import com.czcode.authservice.auth.repository.AuthAccountRepository;
import com.czcode.authservice.auth.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerMockMvcTests {

  private static final short STATUS_ACTIVE = 1;
  private static final String TEST_PASSWORD = "Password123";

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private AuthService authService;

  @Autowired
  private AuthAccountRepository authAccountRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Test
  void authMeShouldReturnUnifiedUnauthorizedWithoutToken() throws Exception {
    mockMvc.perform(get("/api/v1/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().exists("X-Request-Id"))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.requestId").isNotEmpty());
  }

  @Test
  void logoutEndpointShouldRevokeRefreshToken() throws Exception {
    AuthAccountEntity account = createAccount("mockmvc-auth@example.com");
    AuthTokenResponse token = authService.login(
        new LoginRequest(account.getEmail(), TEST_PASSWORD, "web", "chrome"),
        "127.0.0.1",
        "JUnit");

    String logoutJson = objectMapper.writeValueAsString(Map.of("refreshToken", token.refreshToken()));
    mockMvc.perform(
            post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(logoutJson)
                .header("Authorization", "Bearer " + token.accessToken()))
        .andExpect(status().isNoContent())
        .andExpect(header().exists("X-Request-Id"));

    String refreshJson = objectMapper.writeValueAsString(Map.of(
        "refreshToken", token.refreshToken(),
        "clientId", "web",
        "deviceInfo", "chrome"));
    mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshJson))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_REFRESH_TOKEN_INVALID"));
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
}
