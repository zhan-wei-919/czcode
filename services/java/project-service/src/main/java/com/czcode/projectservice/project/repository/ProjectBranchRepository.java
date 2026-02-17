package com.czcode.projectservice.project.repository;

import com.czcode.projectservice.project.entity.ProjectBranchEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectBranchRepository extends JpaRepository<ProjectBranchEntity, UUID> {

  Optional<ProjectBranchEntity> findByIdAndProjectIdAndDeletedAtIsNull(UUID id, UUID projectId);

  Optional<ProjectBranchEntity> findByProjectIdAndNameAndDeletedAtIsNull(UUID projectId, String name);

  List<ProjectBranchEntity> findByProjectIdAndDeletedAtIsNullOrderByUpdatedAtDesc(UUID projectId);

  List<ProjectBranchEntity> findByProjectIdAndStatusAndDeletedAtIsNullOrderByUpdatedAtDesc(
      UUID projectId,
      short status);

  boolean existsByProjectIdAndNameAndDeletedAtIsNull(UUID projectId, String name);
}
