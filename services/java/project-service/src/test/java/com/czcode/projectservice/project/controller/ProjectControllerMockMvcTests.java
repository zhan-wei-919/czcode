package com.czcode.projectservice.project.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.czcode.projectservice.project.dto.CreateProjectRequest;
import com.czcode.projectservice.project.dto.ProjectResponse;
import com.czcode.projectservice.project.repository.ProjectInvitationRepository;
import com.czcode.projectservice.project.service.ProjectService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProjectControllerMockMvcTests {

  private static final String TEST_SECRET = "test-secret-at-least-32-bytes-for-ci";
  private static final SecretKey TEST_SIGNING_KEY = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ProjectService projectService;

  @Autowired
  private ProjectInvitationRepository projectInvitationRepository;

  @Test
  void protectedEndpointShouldReturnUnifiedUnauthorizedError() throws Exception {
    mockMvc.perform(get("/api/v1/projects"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().exists("X-Request-Id"))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.requestId").isNotEmpty());
  }

  @Test
  void createInvitationShouldBeIdempotentByHeaderKey() throws Exception {
    UUID ownerUserId = UUID.randomUUID();
    ProjectResponse project = createProject(ownerUserId);
    String token = jwtToken(ownerUserId);
    String idempotencyKey = "mvc-idem-create-" + UUID.randomUUID();
    String requestJson = objectMapper.writeValueAsString(Map.of(
        "inviteeEmail", "mockmvc-idem@example.com",
        "role", 3,
        "expireHours", 24));

    String firstResponseBody = mockMvc.perform(
            post("/api/v1/projects/{projectId}/invitations", project.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", idempotencyKey))
        .andExpect(status().isCreated())
        .andExpect(header().exists("X-Request-Id"))
        .andReturn()
        .getResponse()
        .getContentAsString();

    String secondResponseBody = mockMvc.perform(
            post("/api/v1/projects/{projectId}/invitations", project.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", idempotencyKey))
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getContentAsString();

    JsonNode first = objectMapper.readTree(firstResponseBody);
    JsonNode second = objectMapper.readTree(secondResponseBody);
    assertThat(second.get("id").asText()).isEqualTo(first.get("id").asText());
    assertThat(projectInvitationRepository.findByProjectIdOrderByCreatedAtDesc(project.id())).hasSize(1);
  }

  @Test
  void createInvitationShouldRejectSameKeyWithDifferentPayload() throws Exception {
    UUID ownerUserId = UUID.randomUUID();
    ProjectResponse project = createProject(ownerUserId);
    String token = jwtToken(ownerUserId);
    String idempotencyKey = "mvc-idem-conflict-" + UUID.randomUUID();
    String firstRequestJson = objectMapper.writeValueAsString(Map.of(
        "inviteeEmail", "mockmvc-conflict@example.com",
        "role", 3,
        "expireHours", 24));
    String secondRequestJson = objectMapper.writeValueAsString(Map.of(
        "inviteeEmail", "mockmvc-conflict@example.com",
        "role", 2,
        "expireHours", 24));

    mockMvc.perform(
            post("/api/v1/projects/{projectId}/invitations", project.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(firstRequestJson)
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", idempotencyKey))
        .andExpect(status().isCreated());

    mockMvc.perform(
            post("/api/v1/projects/{projectId}/invitations", project.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(secondRequestJson)
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", idempotencyKey))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PROJECT_IDEMPOTENCY_CONFLICT"));
  }

  @Test
  void createInvitationShouldRejectWhenIdempotencyKeyMissing() throws Exception {
    UUID ownerUserId = UUID.randomUUID();
    ProjectResponse project = createProject(ownerUserId);
    String token = jwtToken(ownerUserId);
    String requestJson = objectMapper.writeValueAsString(Map.of(
        "inviteeEmail", "missing-idem-header@example.com",
        "role", 3,
        "expireHours", 24));

    mockMvc.perform(
            post("/api/v1/projects/{projectId}/invitations", project.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  private ProjectResponse createProject(UUID ownerUserId) {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    return projectService.createProject(
        ownerUserId,
        new CreateProjectRequest("P_" + suffix, "project-" + suffix, null, ownerUserId, (short) 1));
  }

  private String jwtToken(UUID userId) {
    UUID accountId = UUID.randomUUID();
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(accountId.toString())
        .claim("user_id", userId.toString())
        .claim("email", "mockmvc@example.com")
        .issuedAt(java.util.Date.from(now))
        .expiration(java.util.Date.from(now.plusSeconds(3600)))
        .signWith(TEST_SIGNING_KEY, Jwts.SIG.HS256)
        .compact();
  }
}
