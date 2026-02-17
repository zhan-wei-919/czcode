package com.czcode.projectservice.project.repository;

import com.czcode.projectservice.project.entity.PublishRecordEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublishRecordRepository extends JpaRepository<PublishRecordEntity, UUID> {

  Optional<PublishRecordEntity> findByIdAndProjectId(UUID id, UUID projectId);

  List<PublishRecordEntity> findByProjectIdOrderByPublishedAtDesc(UUID projectId);

  List<PublishRecordEntity> findByProjectIdAndPublishStatusOrderByCreatedAtDesc(UUID projectId, short publishStatus);

  List<PublishRecordEntity> findByProjectIdAndTargetBranchIdOrderByPublishedAtDesc(UUID projectId, UUID targetBranchId);

  List<PublishRecordEntity> findByProjectIdAndTargetBranchIdAndPublishStatusOrderByPublishedAtDesc(
      UUID projectId,
      UUID targetBranchId,
      short publishStatus);

  List<PublishRecordEntity> findByTargetBranchIdOrderByPublishedAtDesc(UUID targetBranchId);
}
