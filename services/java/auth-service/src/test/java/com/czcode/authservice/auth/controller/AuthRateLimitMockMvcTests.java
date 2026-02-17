package com.czcode.authservice.auth.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
    "app.ratelimit.enabled=true",
    "app.ratelimit.max-subjects=1000",
    "app.ratelimit.rules[0].id=auth-send-code-test",
    "app.ratelimit.rules[0].path=/api/v1/auth/send-code",
    "app.ratelimit.rules[0].method=POST",
    "app.ratelimit.rules[0].capacity=1",
    "app.ratelimit.rules[0].refill-tokens=1",
    "app.ratelimit.rules[0].refill-duration-seconds=3600"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthRateLimitMockMvcTests {

  @Autowired
  private MockMvc mockMvc;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void sendCodeShouldReturn429WhenRateLimited() throws Exception {
    String payload = objectMapper.writeValueAsString(Map.of("email", "ratelimit@example.com"));

    mockMvc.perform(post("/api/v1/auth/send-code")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isOk());

    mockMvc.perform(post("/api/v1/auth/send-code")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
  }
}
