package com.czcode.projectservice.project.repository;

import com.czcode.projectservice.project.entity.ProjectInvitationEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectInvitationRepository extends JpaRepository<ProjectInvitationEntity, UUID> {

  Optional<ProjectInvitationEntity> findByIdAndProjectId(UUID id, UUID projectId);

  Optional<ProjectInvitationEntity> findByToken(String token);

  List<ProjectInvitationEntity> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

  List<ProjectInvitationEntity> findByProjectIdAndStatusOrderByCreatedAtDesc(UUID projectId, short status);

  boolean existsByProjectIdAndInviteeEmailAndStatus(UUID projectId, String inviteeEmail, short status);
}
