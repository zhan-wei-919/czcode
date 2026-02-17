package com.czcode.projectservice.project.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "publish_record")
public class PublishRecordEntity {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "source_branch_id", nullable = false)
  private UUID sourceBranchId;

  @Column(name = "target_branch_id", nullable = false)
  private UUID targetBranchId;

  @Column(name = "source_checkpoint_id", nullable = false)
  private UUID sourceCheckpointId;

  @Column(name = "publish_status", nullable = false)
  private short publishStatus;

  @Column(name = "conflict_summary")
  private String conflictSummary;

  @Column(name = "published_by", nullable = false)
  private UUID publishedBy;

  @Column(name = "published_at", nullable = false)
  private Instant publishedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @PrePersist
  public void prePersist() {
    Instant now = Instant.now();
    if (id == null) {
      id = UUID.randomUUID();
    }
    if (publishedAt == null) {
      publishedAt = now;
    }
    if (createdAt == null) {
      createdAt = now;
    }
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public void setProjectId(UUID projectId) {
    this.projectId = projectId;
  }

  public UUID getSourceBranchId() {
    return sourceBranchId;
  }

  public void setSourceBranchId(UUID sourceBranchId) {
    this.sourceBranchId = sourceBranchId;
  }

  public UUID getTargetBranchId() {
    return targetBranchId;
  }

  public void setTargetBranchId(UUID targetBranchId) {
    this.targetBranchId = targetBranchId;
  }

  public UUID getSourceCheckpointId() {
    return sourceCheckpointId;
  }

  public void setSourceCheckpointId(UUID sourceCheckpointId) {
    this.sourceCheckpointId = sourceCheckpointId;
  }

  public short getPublishStatus() {
    return publishStatus;
  }

  public void setPublishStatus(short publishStatus) {
    this.publishStatus = publishStatus;
  }

  public String getConflictSummary() {
    return conflictSummary;
  }

  public void setConflictSummary(String conflictSummary) {
    this.conflictSummary = conflictSummary;
  }

  public UUID getPublishedBy() {
    return publishedBy;
  }

  public void setPublishedBy(UUID publishedBy) {
    this.publishedBy = publishedBy;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }

  public void setPublishedAt(Instant publishedAt) {
    this.publishedAt = publishedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
