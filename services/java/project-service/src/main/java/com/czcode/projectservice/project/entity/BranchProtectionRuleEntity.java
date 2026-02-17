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
@Table(name = "branch_protection_rule")
public class BranchProtectionRuleEntity {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "branch_pattern", nullable = false, length = 128)
  private String branchPattern;

  @Column(name = "min_push_role", nullable = false)
  private short minPushRole;

  @Column(name = "min_merge_role", nullable = false)
  private short minMergeRole;

  @Column(name = "require_pr", nullable = false)
  private boolean requirePr;

  @Column(name = "allow_force_push", nullable = false)
  private boolean allowForcePush;

  @Column(name = "allow_delete_branch", nullable = false)
  private boolean allowDeleteBranch;

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

  public String getBranchPattern() {
    return branchPattern;
  }

  public void setBranchPattern(String branchPattern) {
    this.branchPattern = branchPattern;
  }

  public short getMinPushRole() {
    return minPushRole;
  }

  public void setMinPushRole(short minPushRole) {
    this.minPushRole = minPushRole;
  }

  public short getMinMergeRole() {
    return minMergeRole;
  }

  public void setMinMergeRole(short minMergeRole) {
    this.minMergeRole = minMergeRole;
  }

  public boolean isRequirePr() {
    return requirePr;
  }

  public void setRequirePr(boolean requirePr) {
    this.requirePr = requirePr;
  }

  public boolean isAllowForcePush() {
    return allowForcePush;
  }

  public void setAllowForcePush(boolean allowForcePush) {
    this.allowForcePush = allowForcePush;
  }

  public boolean isAllowDeleteBranch() {
    return allowDeleteBranch;
  }

  public void setAllowDeleteBranch(boolean allowDeleteBranch) {
    this.allowDeleteBranch = allowDeleteBranch;
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
