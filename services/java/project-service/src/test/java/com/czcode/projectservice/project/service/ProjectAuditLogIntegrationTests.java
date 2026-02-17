package com.czcode.projectservice.project.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.czcode.projectservice.project.dto.BranchCheckpointResponse;
import com.czcode.projectservice.project.dto.CreateBranchCheckpointRequest;
import com.czcode.projectservice.project.dto.CreateProjectBranchRequest;
import com.czcode.projectservice.project.dto.CreateProjectInvitationRequest;
import com.czcode.projectservice.project.dto.CreateProjectRequest;
import com.czcode.projectservice.project.dto.CreatePublishRecordRequest;
import com.czcode.projectservice.project.dto.ProjectBranchResponse;
import com.czcode.projectservice.project.dto.ProjectInvitationResponse;
import com.czcode.projectservice.project.dto.ProjectResponse;
import com.czcode.projectservice.project.dto.TransferOwnerRequest;
import com.czcode.projectservice.project.dto.UpsertProjectMemberRequest;
import com.czcode.projectservice.project.entity.ProjectAuditLogEntity;
import com.czcode.projectservice.project.repository.ProjectAuditLogRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProjectAuditLogIntegrationTests {

  private static final short MEMBER_ROLE_MEMBER = 3;
  private static final short BRANCH_TYPE_FEATURE = 2;
  private static final short PUBLISH_STATUS_SUCCESS = 2;

  private static final String ACTION_OWNER_TRANSFERRED = "project.owner_transferred";
  private static final String ACTION_INVITATION_CREATED = "project.invitation_created";
  private static final String ACTION_INVITATION_ACCEPTED = "project.invitation_accepted";
  private static final String ACTION_PUBLISH_CREATED = "project.publish_created";

  @Autowired
  private ProjectService projectService;

  @Autowired
  private BranchPublishService branchPublishService;

  @Autowired
  private ProjectAuditLogRepository projectAuditLogRepository;

  @Test
  void transferOwnerShouldWriteAuditLog() {
    UUID ownerUserId = UUID.randomUUID();
    UUID targetUserId = UUID.randomUUID();
    ProjectResponse project = createProject(ownerUserId);

    projectService.upsertProjectMember(
        ownerUserId,
        project.id(),
        targetUserId,
        new UpsertProjectMemberRequest(MEMBER_ROLE_MEMBER, ownerUserId, (short) 1));

    projectService.transferOwner(ownerUserId, project.id(), new TransferOwnerRequest(targetUserId));

    List<ProjectAuditLogEntity> logs = findLogs(project.id());
    assertThat(logs)
        .anyMatch(log -> ACTION_OWNER_TRANSFERRED.equals(log.getAction()) && ownerUserId.equals(log.getActorUserId()));
  }

  @Test
  void invitationCreateAndAcceptShouldWriteAuditLog() {
    UUID ownerUserId = UUID.randomUUID();
    UUID invitedUserId = UUID.randomUUID();
    String inviteeEmail = "audit-invited@example.com";
    ProjectResponse project = createProject(ownerUserId);

    ProjectInvitationResponse invitation = projectService.createProjectInvitation(
        ownerUserId,
        project.id(),
        new CreateProjectInvitationRequest(inviteeEmail, MEMBER_ROLE_MEMBER, 24));
    projectService.acceptProjectInvitation(invitedUserId, inviteeEmail, invitation.token());

    List<ProjectAuditLogEntity> logs = findLogs(project.id());
    assertThat(logs)
        .anyMatch(log -> ACTION_INVITATION_CREATED.equals(log.getAction()) && ownerUserId.equals(log.getActorUserId()));
    assertThat(logs)
        .anyMatch(log -> ACTION_INVITATION_ACCEPTED.equals(log.getAction()) && invitedUserId.equals(log.getActorUserId()));
  }

  @Test
  void publishShouldWriteAuditLog() {
    UUID ownerUserId = UUID.randomUUID();
    ProjectResponse project = createProject(ownerUserId);

    ProjectBranchResponse mainBranch = branchPublishService.listBranches(ownerUserId, project.id(), null)
        .stream()
        .filter(branch -> "main".equals(branch.name()))
        .findFirst()
        .orElseThrow();

    ProjectBranchResponse featureBranch = branchPublishService.createBranch(
        ownerUserId,
        project.id(),
        new CreateProjectBranchRequest("feature/audit-log", BRANCH_TYPE_FEATURE, mainBranch.id()));

    BranchCheckpointResponse checkpoint = branchPublishService.createCheckpoint(
        ownerUserId,
        project.id(),
        featureBranch.id(),
        new CreateBranchCheckpointRequest(
            "checkpoint-audit",
            "audit publish checkpoint",
            "s3://bucket/checkpoints/audit.tar.zst",
            1024L,
            8));

    branchPublishService.createPublishRecord(
        ownerUserId,
        project.id(),
        new CreatePublishRecordRequest(
            featureBranch.id(),
            mainBranch.id(),
            checkpoint.id(),
            PUBLISH_STATUS_SUCCESS,
            null));

    List<ProjectAuditLogEntity> logs = findLogs(project.id());
    assertThat(logs)
        .anyMatch(log -> ACTION_PUBLISH_CREATED.equals(log.getAction()) && ownerUserId.equals(log.getActorUserId()));
  }

  private ProjectResponse createProject(UUID ownerUserId) {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    return projectService.createProject(
        ownerUserId,
        new CreateProjectRequest("P_" + suffix, "project-" + suffix, null, ownerUserId, (short) 1));
  }

  private List<ProjectAuditLogEntity> findLogs(UUID projectId) {
    return projectAuditLogRepository.findByProjectIdOrderByCreatedAtDesc(projectId, PageRequest.of(0, 200));
  }
}
