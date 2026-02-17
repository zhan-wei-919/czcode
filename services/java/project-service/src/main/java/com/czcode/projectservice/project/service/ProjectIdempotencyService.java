package com.czcode.projectservice.project.service;

import com.czcode.projectservice.project.entity.ProjectIdempotencyRecordEntity;
import com.czcode.projectservice.project.repository.ProjectIdempotencyRecordRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectIdempotencyService {

  private static final int MAX_KEY_LENGTH = 128;

  private final ProjectIdempotencyRecordRepository repository;
  private final ObjectMapper objectMapper = JsonMapper.builder()
      .addModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
      .build();
  private final long ttlSeconds;

  public ProjectIdempotencyService(
      ProjectIdempotencyRecordRepository repository,
      @Value("${project.idempotency.ttl-seconds:86400}") long ttlSeconds) {
    this.repository = repository;
    this.ttlSeconds = ttlSeconds;
  }

  @Transactional
  public <T> T execute(
      UUID projectId,
      UUID actorUserId,
      String scope,
      String idempotencyKey,
      Object requestPayload,
      Class<T> responseType,
      Supplier<T> executor) {
    String normalizedKey = normalizeKey(idempotencyKey);
    if (normalizedKey == null) {
      return executor.get();
    }

    String requestHash = sha256Hex(writeJson(requestPayload));
    ProjectIdempotencyRecordEntity existing = repository
        .findByProjectIdAndActorUserIdAndScopeAndIdempotencyKey(projectId, actorUserId, scope, normalizedKey)
        .orElse(null);
    if (existing != null) {
      return replayExisting(existing, requestHash, responseType);
    }

    T response = executor.get();
    ProjectIdempotencyRecordEntity record = new ProjectIdempotencyRecordEntity();
    record.setProjectId(projectId);
    record.setActorUserId(actorUserId);
    record.setScope(scope);
    record.setIdempotencyKey(normalizedKey);
    record.setRequestHash(requestHash);
    record.setResponseJson(writeJson(response));
    record.setExpiresAt(Instant.now().plusSeconds(ttlSeconds));

    try {
      repository.save(record);
      return response;
    } catch (DataIntegrityViolationException ex) {
      ProjectIdempotencyRecordEntity afterConflict = repository
          .findByProjectIdAndActorUserIdAndScopeAndIdempotencyKey(projectId, actorUserId, scope, normalizedKey)
          .orElseThrow(() -> ex);
      return replayExisting(afterConflict, requestHash, responseType);
    }
  }

  private <T> T replayExisting(ProjectIdempotencyRecordEntity existing, String requestHash, Class<T> responseType) {
    if (!existing.getRequestHash().equals(requestHash)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "idempotency key reused with different request");
    }
    try {
      return objectMapper.readValue(existing.getResponseJson(), responseType);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("failed to deserialize idempotency response", ex);
    }
  }

  private String normalizeKey(String idempotencyKey) {
    if (idempotencyKey == null) {
      return null;
    }
    String normalized = idempotencyKey.trim();
    if (normalized.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key must not be blank");
    }
    if (normalized.length() > MAX_KEY_LENGTH) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Idempotency-Key is too long (max " + MAX_KEY_LENGTH + ")");
    }
    return normalized;
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("failed to serialize idempotency payload", ex);
    }
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
