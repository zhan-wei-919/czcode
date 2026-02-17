package com.czcode.projectservice.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.czcode.projectservice.project.dto.BranchCheckpointResponse;
import com.czcode.projectservice.project.dto.CreateBranchCheckpointRequest;
import com.czcode.projectservice.project.dto.CreateProjectBranchRequest;
import com.czcode.projectservice.project.dto.CreateProjectInvitationRequest;
import com.czcode.projectservice.project.dto.CreateProjectRequest;
import com.czcode.projectservice.project.dto.CreatePublishRecordRequest;
import com.czcode.projectservice.project.dto.ProjectBranchResponse;
import com.czcode.projectservice.project.dto.ProjectInvitationResponse;
import com.czcode.projectservice.project.dto.ProjectMemberResponse;
import com.czcode.projectservice.project.dto.ProjectResponse;
import com.czcode.projectservice.project.dto.PublishRecordResponse;
import com.czcode.projectservice.project.repository.ProjectInvitationRepository;
import com.czcode.projectservice.project.repository.PublishRecordRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProjectIdempotencyIntegrationTests {

  private static final short MEMBER_ROLE_MEMBER = 3;
  private static final short BRANCH_TYPE_FEATURE = 2;
  private static final short PUBLISH_STATUS_SUCCESS = 2;

  @Autowired
  private ProjectService projectService;

  @Autowired
  private BranchPublishService branchPublishService;

  @Autowired
  private ProjectInvitationRepository projectInvitationRepository;

  @Autowired
  private PublishRecordRepository publishRecordRepository;

  @Test
  void createInvitationShouldReturnSameResponseForSameIdempotencyKey() {
    UUID ownerUserId = UUID.randomUUID();
    ProjectResponse project = createProject(ownerUserId);
    String key = "idem-inv-create-" + UUID.randomUUID();

    ProjectInvitationResponse first = projectService.createProjectInvitation(
        ownerUserId,
        project.id(),
        new CreateProjectInvitationRequest("idem-user@example.com", MEMBER_ROLE_MEMBER, 24),
        key);
    ProjectInvitationResponse second = projectService.createProjectInvitation(
        ownerUserId,
        project.id(),
        new CreateProjectInvitationRequest("idem-user@example.com", MEMBER_ROLE_MEMBER, 24),
        key);

    assertThat(second.id()).isEqualTo(first.id());
    long invitationCount = projectInvitationRepository.findByProjectIdOrderByCreatedAtDesc(project.id()).size();
    assertThat(invitationCount).isEqualTo(1L);
  }

  @Test
  void acceptInvitationShouldReturnSameResponseForSameIdempotencyKey() {
    UUID ownerUserId = UUID.randomUUID();
    UUID invitedUserId = UUID.randomUUID();
    String inviteeEmail = "idem-accept@example.com";
    String key = "idem-inv-accept-" + UUID.randomUUID();
    ProjectResponse project = createProject(ownerUserId);

    ProjectInvitationResponse invitation = projectService.createProjectInvitation(
        ownerUserId,
        project.id(),
        new CreateProjectInvitationRequest(inviteeEmail, MEMBER_ROLE_MEMBER, 24));

    ProjectMemberResponse first = projectService.acceptProjectInvitation(
        invitedUserId,
        inviteeEmail,
        invitation.token(),
        key);
    ProjectMemberResponse second = projectService.acceptProjectInvitation(
        invitedUserId,
        inviteeEmail,
        invitation.token(),
        key);

    assertThat(second.projectId()).isEqualTo(first.projectId());
    assertThat(second.userId()).isEqualTo(first.userId());
    assertThat(second.role()).isEqualTo(first.role());
  }

  @Test
  void publishShouldReturnSameResponseForSameIdempotencyKey() {
    UUID ownerUserId = UUID.randomUUID();
    ProjectResponse project = createProject(ownerUserId);
    String key = "idem-publish-" + UUID.randomUUID();

    ProjectBranchResponse mainBranch = branchPublishService.listBranches(ownerUserId, project.id(), null)
        .stream()
        .filter(branch -> "main".equals(branch.name()))
        .findFirst()
        .orElseThrow();
    ProjectBranchResponse featureBranch = branchPublishService.createBranch(
        ownerUserId,
        project.id(),
        new CreateProjectBranchRequest("feature/idem-publish", BRANCH_TYPE_FEATURE, mainBranch.id()));
    BranchCheckpointResponse checkpoint = branchPublishService.createCheckpoint(
        ownerUserId,
        project.id(),
        featureBranch.id(),
        new CreateBranchCheckpointRequest(
            "idem-cp",
            "idempotency checkpoint",
            "s3://bucket/checkpoints/idem-cp.tar.zst",
            2048L,
            12));

    PublishRecordResponse first = branchPublishService.createPublishRecord(
        ownerUserId,
        project.id(),
        new CreatePublishRecordRequest(
            featureBranch.id(),
            mainBranch.id(),
            checkpoint.id(),
            PUBLISH_STATUS_SUCCESS,
            null),
        key);
    PublishRecordResponse second = branchPublishService.createPublishRecord(
        ownerUserId,
        project.id(),
        new CreatePublishRecordRequest(
            featureBranch.id(),
            mainBranch.id(),
            checkpoint.id(),
            PUBLISH_STATUS_SUCCESS,
            null),
        key);

    assertThat(second.id()).isEqualTo(first.id());
    long publishCount = publishRecordRepository.findByProjectIdOrderByPublishedAtDesc(project.id()).size();
    assertThat(publishCount).isEqualTo(1L);
  }

  @Test
  void createInvitationShouldRejectSameKeyWithDifferentPayload() {
    UUID ownerUserId = UUID.randomUUID();
    ProjectResponse project = createProject(ownerUserId);
    String key = "idem-conflict-" + UUID.randomUUID();

    projectService.createProjectInvitation(
        ownerUserId,
        project.id(),
        new CreateProjectInvitationRequest("same-key@example.com", MEMBER_ROLE_MEMBER, 24),
        key);

    ResponseStatusException ex = assertThrows(
        ResponseStatusException.class,
        () -> projectService.createProjectInvitation(
            ownerUserId,
            project.id(),
            new CreateProjectInvitationRequest("same-key@example.com", (short) 2, 24),
            key));
    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  private ProjectResponse createProject(UUID ownerUserId) {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    return projectService.createProject(
        ownerUserId,
        new CreateProjectRequest("P_" + suffix, "project-" + suffix, null, ownerUserId, (short) 1));
  }
}
