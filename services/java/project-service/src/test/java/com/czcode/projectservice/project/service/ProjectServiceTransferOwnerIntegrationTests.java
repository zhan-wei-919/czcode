package com.czcode.projectservice.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.czcode.projectservice.project.dto.CreateProjectRequest;
import com.czcode.projectservice.project.dto.ProjectResponse;
import com.czcode.projectservice.project.dto.TransferOwnerRequest;
import com.czcode.projectservice.project.dto.UpsertProjectMemberRequest;
import com.czcode.projectservice.project.entity.ProjectMemberEntity;
import com.czcode.projectservice.project.repository.ProjectMemberRepository;
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
class ProjectServiceTransferOwnerIntegrationTests {

  private static final short MEMBER_ROLE_OWNER = 1;
  private static final short MEMBER_ROLE_ADMIN = 2;
  private static final short MEMBER_ROLE_MEMBER = 3;

  @Autowired
  private ProjectService projectService;

  @Autowired
  private ProjectMemberRepository projectMemberRepository;

  @Test
  void transferOwnerShouldDemoteOldOwnerToMemberWhenOtherAdminExists() {
    UUID ownerUserId = UUID.randomUUID();
    UUID adminUserId = UUID.randomUUID();
    UUID targetUserId = UUID.randomUUID();
    ProjectResponse project = createProject(ownerUserId);

    projectService.upsertProjectMember(
        ownerUserId,
        project.id(),
        adminUserId,
        new UpsertProjectMemberRequest(MEMBER_ROLE_ADMIN, ownerUserId, (short) 1));
    projectService.upsertProjectMember(
        ownerUserId,
        project.id(),
        targetUserId,
        new UpsertProjectMemberRequest(MEMBER_ROLE_MEMBER, ownerUserId, (short) 1));

    ProjectResponse transferred = projectService.transferOwner(
        ownerUserId,
        project.id(),
        new TransferOwnerRequest(targetUserId));

    assertThat(transferred.ownerUserId()).isEqualTo(targetUserId);
    ProjectMemberEntity oldOwner = projectMemberRepository.findByIdProjectIdAndIdUserId(project.id(), ownerUserId)
        .orElseThrow();
    ProjectMemberEntity newOwner = projectMemberRepository.findByIdProjectIdAndIdUserId(project.id(), targetUserId)
        .orElseThrow();
    assertThat(oldOwner.getRole()).isEqualTo(MEMBER_ROLE_MEMBER);
    assertThat(newOwner.getRole()).isEqualTo(MEMBER_ROLE_OWNER);
  }

  @Test
  void transferOwnerShouldDemoteOldOwnerToAdminWhenNoOtherAdminExists() {
    UUID ownerUserId = UUID.randomUUID();
    UUID targetUserId = UUID.randomUUID();
    ProjectResponse project = createProject(ownerUserId);

    projectService.upsertProjectMember(
        ownerUserId,
        project.id(),
        targetUserId,
        new UpsertProjectMemberRequest(MEMBER_ROLE_MEMBER, ownerUserId, (short) 1));

    ProjectResponse transferred = projectService.transferOwner(
        ownerUserId,
        project.id(),
        new TransferOwnerRequest(targetUserId));

    assertThat(transferred.ownerUserId()).isEqualTo(targetUserId);
    ProjectMemberEntity oldOwner = projectMemberRepository.findByIdProjectIdAndIdUserId(project.id(), ownerUserId)
        .orElseThrow();
    ProjectMemberEntity newOwner = projectMemberRepository.findByIdProjectIdAndIdUserId(project.id(), targetUserId)
        .orElseThrow();
    assertThat(oldOwner.getRole()).isEqualTo(MEMBER_ROLE_ADMIN);
    assertThat(newOwner.getRole()).isEqualTo(MEMBER_ROLE_OWNER);
  }

  @Test
  void transferOwnerShouldRejectNonOwnerOperator() {
    UUID ownerUserId = UUID.randomUUID();
    UUID adminUserId = UUID.randomUUID();
    UUID targetUserId = UUID.randomUUID();
    ProjectResponse project = createProject(ownerUserId);

    projectService.upsertProjectMember(
        ownerUserId,
        project.id(),
        adminUserId,
        new UpsertProjectMemberRequest(MEMBER_ROLE_ADMIN, ownerUserId, (short) 1));
    projectService.upsertProjectMember(
        ownerUserId,
        project.id(),
        targetUserId,
        new UpsertProjectMemberRequest(MEMBER_ROLE_MEMBER, ownerUserId, (short) 1));

    ResponseStatusException ex = assertThrows(
        ResponseStatusException.class,
        () -> projectService.transferOwner(adminUserId, project.id(), new TransferOwnerRequest(targetUserId)));

    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  private ProjectResponse createProject(UUID ownerUserId) {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    return projectService.createProject(
        ownerUserId,
        new CreateProjectRequest("P_" + suffix, "project-" + suffix, null, ownerUserId, (short) 1));
  }
}
