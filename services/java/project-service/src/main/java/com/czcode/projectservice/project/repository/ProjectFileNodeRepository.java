package com.czcode.projectservice.project.repository;

import com.czcode.projectservice.project.entity.ProjectFileNodeEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectFileNodeRepository extends JpaRepository<ProjectFileNodeEntity, UUID> {

  Optional<ProjectFileNodeEntity> findByIdAndProjectIdAndDeletedAtIsNull(UUID id, UUID projectId);

  List<ProjectFileNodeEntity> findByProjectIdAndParentIdIsNullAndDeletedAtIsNullOrderByDirectoryDescNameAsc(
      UUID projectId);

  List<ProjectFileNodeEntity> findByProjectIdAndParentIdAndDeletedAtIsNullOrderByDirectoryDescNameAsc(
      UUID projectId,
      UUID parentId);

  List<ProjectFileNodeEntity> findByProjectIdAndPathStartingWithAndDeletedAtIsNull(
      UUID projectId,
      String pathPrefix);

  List<ProjectFileNodeEntity> findByProjectIdAndDeletedAtIsNullOrderByPathAsc(UUID projectId);

  boolean existsByProjectIdAndPathAndDeletedAtIsNull(UUID projectId, String path);
}
