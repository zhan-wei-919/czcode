package com.czcode.projectservice.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.czcode.projectservice.project.dto.BranchCheckpointResponse;
import com.czcode.projectservice.project.dto.CreateBranchCheckpointRequest;
import com.czcode.projectservice.project.dto.CreateProjectBranchRequest;
import com.czcode.projectservice.project.dto.CreatePublishRecordRequest;
import com.czcode.projectservice.project.dto.ProjectBranchResponse;
import com.czcode.projectservice.project.dto.PublishRecordResponse;
import com.czcode.projectservice.project.entity.ProjectBranchEntity;
import com.czcode.projectservice.project.entity.ProjectEntity;
import com.czcode.projectservice.project.entity.ProjectMemberEntity;
import com.czcode.projectservice.project.entity.ProjectMemberId;
import com.czcode.projectservice.project.repository.ProjectBranchRepository;
import com.czcode.projectservice.project.repository.ProjectMemberRepository;
import com.czcode.projectservice.project.repository.ProjectRepository;
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
class BranchPublishServiceIntegrationTests {

  private static final short PROJECT_VISIBILITY_PRIVATE = 1;
  private static final short PROJECT_STATUS_ACTIVE = 1;
  private static final short MEMBER_ROLE_OWNER = 1;
  private static final short MEMBER_ROLE_VIEWER = 4;
  private static final short MEMBER_STATUS_ACTIVE = 1;
  private static final short BRANCH_TYPE_MAIN = 1;
  private static final short BRANCH_TYPE_FEATURE = 2;
  private static final short BRANCH_STATUS_ACTIVE = 1;
  private static final short PUBLISH_STATUS_SUCCESS = 2;

  @Autowired
  private BranchPublishService branchPublishService;

  @Autowired
  private ProjectRepository projectRepository;

  @Autowired
  private ProjectMemberRepository projectMemberRepository;

  @Autowired
  private ProjectBranchRepository projectBranchRepository;

  @Test
  void deleteBranchShouldRejectViewerRole() {
    UUID ownerUserId = UUID.randomUUID();
    UUID viewerUserId = UUID.randomUUID();
    ProjectEntity project = createProject(ownerUserId);
    addMember(project.getId(), ownerUserId, MEMBER_ROLE_OWNER, ownerUserId);
    addMember(project.getId(), viewerUserId, MEMBER_ROLE_VIEWER, ownerUserId);

    ProjectBranchEntity featureBranch = createBranch(project.getId(), "feature/perm", BRANCH_TYPE_FEATURE, ownerUserId);

    ResponseStatusException ex = assertThrows(
        ResponseStatusException.class,
        () -> branchPublishService.deleteBranch(viewerUserId, project.getId(), featureBranch.getId()));

    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    ProjectBranchEntity branchAfter = projectBranchRepository.findById(featureBranch.getId()).orElseThrow();
    assertThat(branchAfter.getDeletedAt()).isNull();
  }

  @Test
  void deleteBranchShouldRejectMainBranch() {
    UUID ownerUserId = UUID.randomUUID();
    ProjectEntity project = createProject(ownerUserId);
    addMember(project.getId(), ownerUserId, MEMBER_ROLE_OWNER, ownerUserId);
    ProjectBranchEntity mainBranch = createBranch(project.getId(), "main", BRANCH_TYPE_MAIN, ownerUserId);

    ResponseStatusException ex = assertThrows(
        ResponseStatusException.class,
        () -> branchPublishService.deleteBranch(ownerUserId, project.getId(), mainBranch.getId()));

    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    ProjectBranchEntity branchAfter = projectBranchRepository.findById(mainBranch.getId()).orElseThrow();
    assertThat(branchAfter.getDeletedAt()).isNull();
  }

  @Test
  void createCheckpointShouldAdvanceBranchHeadCheckpoint() {
    UUID ownerUserId = UUID.randomUUID();
    ProjectEntity project = createProject(ownerUserId);
    addMember(project.getId(), ownerUserId, MEMBER_ROLE_OWNER, ownerUserId);

    ProjectBranchResponse featureBranch = branchPublishService.createBranch(
        ownerUserId,
        project.getId(),
        new CreateProjectBranchRequest("feature/checkpoint", BRANCH_TYPE_FEATURE, null));

    BranchCheckpointResponse checkpoint = branchPublishService.createCheckpoint(
        ownerUserId,
        project.getId(),
        featureBranch.id(),
        new CreateBranchCheckpointRequest(
            "checkpoint-1",
            "initial snapshot",
            "s3://bucket/checkpoints/cp-1.tar.zst",
            1024L,
            7));

    ProjectBranchEntity branchAfter = projectBranchRepository
        .findByIdAndProjectIdAndDeletedAtIsNull(featureBranch.id(), project.getId())
        .orElseThrow();
    assertThat(branchAfter.getHeadCheckpointId()).isEqualTo(checkpoint.id());
  }

  @Test
  void publishSuccessShouldAdvanceTargetBranchHeadCheckpoint() {
    UUID ownerUserId = UUID.randomUUID();
    ProjectEntity project = createProject(ownerUserId);
    addMember(project.getId(), ownerUserId, MEMBER_ROLE_OWNER, ownerUserId);

    ProjectBranchResponse mainBranch = branchPublishService.createBranch(
        ownerUserId,
        project.getId(),
        new CreateProjectBranchRequest("main", BRANCH_TYPE_MAIN, null));
    ProjectBranchResponse featureBranch = branchPublishService.createBranch(
        ownerUserId,
        project.getId(),
        new CreateProjectBranchRequest("feature/publish", BRANCH_TYPE_FEATURE, mainBranch.id()));

    BranchCheckpointResponse checkpoint = branchPublishService.createCheckpoint(
        ownerUserId,
        project.getId(),
        featureBranch.id(),
        new CreateBranchCheckpointRequest(
            "checkpoint-2",
            "publish snapshot",
            "s3://bucket/checkpoints/cp-2.tar.zst",
            2048L,
            12));

    PublishRecordResponse publish = branchPublishService.createPublishRecord(
        ownerUserId,
        project.getId(),
        new CreatePublishRecordRequest(
            featureBranch.id(),
            mainBranch.id(),
            checkpoint.id(),
            PUBLISH_STATUS_SUCCESS,
            null));

    assertThat(publish.publishStatus()).isEqualTo(PUBLISH_STATUS_SUCCESS);
    ProjectBranchEntity mainAfter = projectBranchRepository
        .findByIdAndProjectIdAndDeletedAtIsNull(mainBranch.id(), project.getId())
        .orElseThrow();
    assertThat(mainAfter.getHeadCheckpointId()).isEqualTo(checkpoint.id());
  }

  private ProjectEntity createProject(UUID ownerUserId) {
    ProjectEntity project = new ProjectEntity();
    project.setProjectKey("P_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
    project.setName("project-" + UUID.randomUUID().toString().substring(0, 8));
    project.setOwnerUserId(ownerUserId);
    project.setVisibility(PROJECT_VISIBILITY_PRIVATE);
    project.setStatus(PROJECT_STATUS_ACTIVE);
    return projectRepository.save(project);
  }

  private void addMember(UUID projectId, UUID userId, short role, UUID inviterUserId) {
    ProjectMemberEntity member = new ProjectMemberEntity();
    member.setId(new ProjectMemberId(projectId, userId));
    member.setRole(role);
    member.setStatus(MEMBER_STATUS_ACTIVE);
    member.setInviterUserId(inviterUserId);
    projectMemberRepository.save(member);
  }

  private ProjectBranchEntity createBranch(UUID projectId, String name, short branchType, UUID createdBy) {
    ProjectBranchEntity branch = new ProjectBranchEntity();
    branch.setProjectId(projectId);
    branch.setName(name);
    branch.setBranchType(branchType);
    branch.setStatus(BRANCH_STATUS_ACTIVE);
    branch.setCreatedBy(createdBy);
    return projectBranchRepository.save(branch);
  }
}
