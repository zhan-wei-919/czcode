package com.czcode.projectservice.project.repository;

import com.czcode.projectservice.project.entity.BranchCheckpointEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchCheckpointRepository extends JpaRepository<BranchCheckpointEntity, UUID> {

  Optional<BranchCheckpointEntity> findByIdAndProjectIdAndDeletedAtIsNull(UUID id, UUID projectId);

  List<BranchCheckpointEntity> findByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID projectId);

  List<BranchCheckpointEntity> findByProjectIdAndBranchIdAndDeletedAtIsNullOrderByCreatedAtDesc(
      UUID projectId,
      UUID branchId);
}
