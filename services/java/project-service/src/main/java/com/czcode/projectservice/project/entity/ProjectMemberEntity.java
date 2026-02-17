package com.czcode.projectservice.project.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_member")
public class ProjectMemberEntity {

  @EmbeddedId
  private ProjectMemberId id;

  @Column(nullable = false)
  private short role;

  @Column(name = "joined_at", nullable = false)
  private Instant joinedAt;

  @Column(name = "inviter_user_id")
  private UUID inviterUserId;

  @Column(nullable = false)
  private short status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  public void prePersist() {
    Instant now = Instant.now();
    if (joinedAt == null) {
      joinedAt = now;
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

  public ProjectMemberId getId() {
    return id;
  }

  public void setId(ProjectMemberId id) {
    this.id = id;
  }

  public short getRole() {
    return role;
  }

  public void setRole(short role) {
    this.role = role;
  }

  public Instant getJoinedAt() {
    return joinedAt;
  }

  public void setJoinedAt(Instant joinedAt) {
    this.joinedAt = joinedAt;
  }

  public UUID getInviterUserId() {
    return inviterUserId;
  }

  public void setInviterUserId(UUID inviterUserId) {
    this.inviterUserId = inviterUserId;
  }

  public short getStatus() {
    return status;
  }

  public void setStatus(short status) {
    this.status = status;
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
}
