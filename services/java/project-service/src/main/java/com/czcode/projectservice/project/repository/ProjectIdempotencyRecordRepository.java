package com.czcode.projectservice.project.repository;

import com.czcode.projectservice.project.entity.ProjectIdempotencyRecordEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectIdempotencyRecordRepository extends JpaRepository<ProjectIdempotencyRecordEntity, UUID> {

  Optional<ProjectIdempotencyRecordEntity> findByProjectIdAndActorUserIdAndScopeAndIdempotencyKey(
      UUID projectId,
      UUID actorUserId,
      String scope,
      String idempotencyKey);

  List<ProjectIdempotencyRecordEntity> findByExpiresAtBeforeOrderByExpiresAtAsc(Instant instant, Pageable pageable);
}
