package com.czcode.userservice.user.service;

import com.czcode.userservice.user.dto.CreateUserProfileRequest;
import com.czcode.userservice.user.dto.UpdateUserProfileRequest;
import com.czcode.userservice.user.dto.UserProfileResponse;
import com.czcode.userservice.user.entity.UserProfileEntity;
import com.czcode.userservice.user.repository.UserProfileRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserProfileService {

  private static final short STATUS_ACTIVE = 1;
  private static final short STATUS_INACTIVE = 2;

  private final UserProfileRepository userProfileRepository;

  public UserProfileService(UserProfileRepository userProfileRepository) {
    this.userProfileRepository = userProfileRepository;
  }

  @Transactional
  public UserProfileResponse create(CreateUserProfileRequest request) {
    UserProfileEntity entity = new UserProfileEntity();
    entity.setNickname(request.nickname());
    entity.setAvatarUrl(request.avatarUrl());
    entity.setBio(request.bio());
    entity.setTimezone(normalizeOrDefault(request.timezone(), "UTC"));
    entity.setLocale(normalizeOrDefault(request.locale(), "zh-CN"));
    entity.setStatus(validateStatusOrDefault(request.status(), STATUS_ACTIVE));

    return toResponse(userProfileRepository.save(entity));
  }

  @Transactional(readOnly = true)
  public UserProfileResponse get(UUID userId) {
    UserProfileEntity entity = findActiveById(userId);
    return toResponse(entity);
  }

  @Transactional(readOnly = true)
  public List<UserProfileResponse> list(Short status) {
    List<UserProfileEntity> entities;
    if (status == null) {
      entities = userProfileRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc();
    } else {
      entities = userProfileRepository.findAllByStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
          validateStatusOrDefault(status, STATUS_ACTIVE));
    }
    return entities.stream().map(this::toResponse).toList();
  }

  @Transactional
  public UserProfileResponse update(UUID userId, UpdateUserProfileRequest request) {
    UserProfileEntity entity = findActiveById(userId);

    if (request.nickname() != null) {
      entity.setNickname(request.nickname());
    }
    if (request.avatarUrl() != null) {
      entity.setAvatarUrl(request.avatarUrl());
    }
    if (request.bio() != null) {
      entity.setBio(request.bio());
    }
    if (request.timezone() != null) {
      entity.setTimezone(normalizeOrDefault(request.timezone(), entity.getTimezone()));
    }
    if (request.locale() != null) {
      entity.setLocale(normalizeOrDefault(request.locale(), entity.getLocale()));
    }
    if (request.status() != null) {
      entity.setStatus(validateStatusOrDefault(request.status(), entity.getStatus()));
    }

    return toResponse(userProfileRepository.save(entity));
  }

  @Transactional
  public void delete(UUID userId) {
    UserProfileEntity entity = findActiveById(userId);
    entity.setDeletedAt(Instant.now());
    userProfileRepository.save(entity);
  }

  private UserProfileEntity findActiveById(UUID userId) {
    return userProfileRepository.findByIdAndDeletedAtIsNull(userId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));
  }

  private short validateStatusOrDefault(Short status, short defaultValue) {
    short value = status == null ? defaultValue : status;
    if (value != STATUS_ACTIVE && value != STATUS_INACTIVE) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid status");
    }
    return value;
  }

  private String normalizeOrDefault(String value, String defaultValue) {
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    return value;
  }

  private UserProfileResponse toResponse(UserProfileEntity entity) {
    return new UserProfileResponse(
        entity.getId(),
        entity.getNickname(),
        entity.getAvatarUrl(),
        entity.getBio(),
        entity.getTimezone(),
        entity.getLocale(),
        entity.getStatus(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
