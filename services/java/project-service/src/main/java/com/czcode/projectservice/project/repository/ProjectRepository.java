package com.czcode.projectservice.project.repository;

import com.czcode.projectservice.project.entity.ProjectEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<ProjectEntity, UUID> {

  Optional<ProjectEntity> findByIdAndDeletedAtIsNull(UUID id);

  List<ProjectEntity> findAllByDeletedAtIsNullOrderByUpdatedAtDesc();

  boolean existsByProjectKeyAndDeletedAtIsNull(String projectKey);
}
