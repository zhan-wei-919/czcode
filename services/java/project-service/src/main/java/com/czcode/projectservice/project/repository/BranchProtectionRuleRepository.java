package com.czcode.projectservice.project.repository;

import com.czcode.projectservice.project.entity.BranchProtectionRuleEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchProtectionRuleRepository extends JpaRepository<BranchProtectionRuleEntity, UUID> {

  Optional<BranchProtectionRuleEntity> findByIdAndProjectIdAndDeletedAtIsNull(UUID id, UUID projectId);

  Optional<BranchProtectionRuleEntity> findByProjectIdAndBranchPatternAndDeletedAtIsNull(
      UUID projectId,
      String branchPattern);

  List<BranchProtectionRuleEntity> findByProjectIdAndDeletedAtIsNullOrderByUpdatedAtDesc(UUID projectId);

  boolean existsByProjectIdAndBranchPatternAndDeletedAtIsNull(UUID projectId, String branchPattern);
}
