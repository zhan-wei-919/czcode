package com.czcode.projectservice.project.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_branch")
public class ProjectBranchEntity {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(nullable = false, length = 128)
  private String name;

  @Column(name = "branch_type", nullable = false)
  private short branchType;

  @Column(name = "based_on_branch_id")
  private UUID basedOnBranchId;

  @Column(name = "head_checkpoint_id")
  private UUID headCheckpointId;

  @Column(nullable = false)
  private short status;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @PrePersist
  public void prePersist() {
    Instant now = Instant.now();
    if (id == null) {
      id = UUID.randomUUID();
    }
    if (status == 0) {
      status = 1;
    }
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  public void preUpdate() {
    updatedAt = Instant.now();
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

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public short getBranchType() {
    return branchType;
  }

  public void setBranchType(short branchType) {
    this.branchType = branchType;
  }

  public UUID getBasedOnBranchId() {
    return basedOnBranchId;
  }

  public void setBasedOnBranchId(UUID basedOnBranchId) {
    this.basedOnBranchId = basedOnBranchId;
  }

  public UUID getHeadCheckpointId() {
    return headCheckpointId;
  }

  public void setHeadCheckpointId(UUID headCheckpointId) {
    this.headCheckpointId = headCheckpointId;
  }

  public short getStatus() {
    return status;
  }

  public void setStatus(short status) {
    this.status = status;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(UUID createdBy) {
    this.createdBy = createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(Instant deletedAt) {
    this.deletedAt = deletedAt;
  }
}
