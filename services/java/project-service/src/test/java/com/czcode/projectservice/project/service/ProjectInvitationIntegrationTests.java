package com.czcode.projectservice.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.czcode.projectservice.project.dto.CreateProjectInvitationRequest;
import com.czcode.projectservice.project.dto.CreateProjectRequest;
import com.czcode.projectservice.project.dto.ProjectInvitationResponse;
import com.czcode.projectservice.project.dto.ProjectMemberResponse;
import com.czcode.projectservice.project.dto.ProjectResponse;
import com.czcode.projectservice.project.dto.UpsertProjectMemberRequest;
import com.czcode.projectservice.project.entity.ProjectInvitationEntity;
import com.czcode.projectservice.project.repository.ProjectInvitationRepository;
import java.time.Instant;
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
class ProjectInvitationIntegrationTests {

  private static final short MEMBER_ROLE_ADMIN = 2;
  private static final short MEMBER_ROLE_MEMBER = 3;
  private static final short MEMBER_ROLE_VIEWER = 4;

  private static final short INVITATION_STATUS_PENDING = 1;
  private static final short INVITATION_STATUS_ACCEPTED = 2;
  private static final short INVITATION_STATUS_EXPIRED = 4;

  @Autowired
  private ProjectService projectService;

  @Autowired
  private ProjectInvitationRepository projectInvitationRepository;

  @Test
  void createAndAcceptInvitationShouldAddProjectMember() {
    UUID ownerUserId = UUID.randomUUID();
    UUID invitedUserId = UUID.randomUUID();
    String invitedEmail = "invited-user@example.com";
    ProjectResponse project = createProject(ownerUserId);

    ProjectInvitationResponse invitation = projectService.createProjectInvitation(
        ownerUserId,
        project.id(),
        new CreateProjectInvitationRequest(invitedEmail, MEMBER_ROLE_MEMBER, 24));

    ProjectMemberResponse member = projectService.acceptProjectInvitation(
        invitedUserId,
        invitedEmail,
        invitation.token());

    assertThat(member.projectId()).isEqualTo(project.id());
    assertThat(member.userId()).isEqualTo(invitedUserId);
    assertThat(member.role()).isEqualTo(MEMBER_ROLE_MEMBER);
    assertThat(member.status()).isEqualTo((short) 1);

    ProjectInvitationEntity invitationAfter = projectInvitationRepository.findByToken(invitation.token()).orElseThrow();
    assertThat(invitationAfter.getStatus()).isEqualTo(INVITATION_STATUS_ACCEPTED);
    assertThat(invitationAfter.getAcceptedAt()).isNotNull();
  }

  @Test
  void createInvitationShouldRejectViewer() {
    UUID ownerUserId = UUID.randomUUID();
    UUID viewerUserId = UUID.randomUUID();
    ProjectResponse project = createProject(ownerUserId);

    projectService.upsertProjectMember(
        ownerUserId,
        project.id(),
        viewerUserId,
        new UpsertProjectMemberRequest(MEMBER_ROLE_VIEWER, ownerUserId, (short) 1));

    ResponseStatusException ex = assertThrows(
        ResponseStatusException.class,
        () -> projectService.createProjectInvitation(
            viewerUserId,
            project.id(),
            new CreateProjectInvitationRequest("viewer-test@example.com", MEMBER_ROLE_MEMBER, 24)));

    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void acceptInvitationShouldRejectEmailMismatch() {
    UUID ownerUserId = UUID.randomUUID();
    UUID invitedUserId = UUID.randomUUID();
    ProjectResponse project = createProject(ownerUserId);

    ProjectInvitationResponse invitation = projectService.createProjectInvitation(
        ownerUserId,
        project.id(),
        new CreateProjectInvitationRequest("real@example.com", MEMBER_ROLE_MEMBER, 24));

    ResponseStatusException ex = assertThrows(
        ResponseStatusException.class,
        () -> projectService.acceptProjectInvitation(invitedUserId, "another@example.com", invitation.token()));
    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    ProjectInvitationEntity invitationAfter = projectInvitationRepository.findByToken(invitation.token()).orElseThrow();
    assertThat(invitationAfter.getStatus()).isEqualTo(INVITATION_STATUS_PENDING);
    assertThat(invitationAfter.getAcceptedAt()).isNull();
  }

  @Test
  void acceptInvitationShouldMarkExpiredWhenTokenExpired() {
    UUID ownerUserId = UUID.randomUUID();
    UUID invitedUserId = UUID.randomUUID();
    String invitedEmail = "exp-user@example.com";
    ProjectResponse project = createProject(ownerUserId);

    ProjectInvitationResponse invitation = projectService.createProjectInvitation(
        ownerUserId,
        project.id(),
        new CreateProjectInvitationRequest(invitedEmail, MEMBER_ROLE_MEMBER, 24));

    ProjectInvitationEntity entity = projectInvitationRepository.findByToken(invitation.token()).orElseThrow();
    entity.setExpiredAt(Instant.now().minusSeconds(30));
    projectInvitationRepository.save(entity);

    ResponseStatusException ex = assertThrows(
        ResponseStatusException.class,
        () -> projectService.acceptProjectInvitation(invitedUserId, invitedEmail, invitation.token()));
    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    ProjectInvitationEntity invitationAfter = projectInvitationRepository.findByToken(invitation.token()).orElseThrow();
    assertThat(invitationAfter.getStatus()).isEqualTo(INVITATION_STATUS_EXPIRED);
    assertThat(invitationAfter.getAcceptedAt()).isNull();
  }

  private ProjectResponse createProject(UUID ownerUserId) {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    return projectService.createProject(
        ownerUserId,
        new CreateProjectRequest("P_" + suffix, "project-" + suffix, null, ownerUserId, (short) 1));
  }
}
