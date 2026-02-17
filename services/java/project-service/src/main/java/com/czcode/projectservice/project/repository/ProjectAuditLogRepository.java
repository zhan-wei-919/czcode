package com.czcode.projectservice.project.repository;

import com.czcode.projectservice.project.entity.ProjectAuditLogEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectAuditLogRepository extends JpaRepository<ProjectAuditLogEntity, UUID> {

  List<ProjectAuditLogEntity> findByProjectIdOrderByCreatedAtDesc(UUID projectId, Pageable pageable);
}
