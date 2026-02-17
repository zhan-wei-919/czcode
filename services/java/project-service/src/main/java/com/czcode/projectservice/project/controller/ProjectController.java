package com.czcode.projectservice.project.controller;

import com.czcode.projectservice.project.dto.CreateFileNodeRequest;
import com.czcode.projectservice.project.dto.CreateProjectInvitationRequest;
import com.czcode.projectservice.project.dto.CreateProjectRequest;
import com.czcode.projectservice.project.dto.CurrentUserResponse;
import com.czcode.projectservice.project.dto.FileNodeResponse;
import com.czcode.projectservice.project.dto.ProjectAuditLogResponse;
import com.czcode.projectservice.project.dto.ProjectInvitationResponse;
import com.czcode.projectservice.project.dto.ProjectMemberResponse;
import com.czcode.projectservice.project.dto.ProjectResponse;
import com.czcode.projectservice.project.dto.TransferOwnerRequest;
import com.czcode.projectservice.project.dto.UpdateFileNodeRequest;
import com.czcode.projectservice.project.dto.UpdateProjectRequest;
import com.czcode.projectservice.project.dto.UpsertProjectMemberRequest;
import com.czcode.projectservice.project.service.ProjectService;
import com.czcode.projectservice.security.AuthPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

  private final ProjectService projectService;

  public ProjectController(ProjectService projectService) {
    this.projectService = projectService;
  }

  @GetMapping("/me")
  public CurrentUserResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
    if (principal == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized");
    }
    return new CurrentUserResponse(principal.accountId(), principal.userId(), principal.email());
  }

  @PostMapping
  public ResponseEntity<ProjectResponse> createProject(
      @AuthenticationPrincipal AuthPrincipal principal,
      @Valid @RequestBody CreateProjectRequest request) {
    ProjectResponse created = projectService.createProject(requireCurrentUserId(principal), request);
    return ResponseEntity.created(URI.create("/api/v1/projects/" + created.id())).body(created);
  }

  @GetMapping("/{projectId}")
  public ProjectResponse getProject(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable UUID projectId) {
    return projectService.getProject(requireCurrentUserId(principal), projectId);
  }

  @GetMapping
  public List<ProjectResponse> listProjects(@AuthenticationPrincipal AuthPrincipal principal) {
    return projectService.listProjects(requireCurrentUserId(principal));
  }

  @PatchMapping("/{projectId}")
  public ProjectResponse updateProject(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable UUID projectId,
      @Valid @RequestBody UpdateProjectRequest request) {
    return projectService.updateProject(requireCurrentUserId(principal), projectId, request);
  }

  @DeleteMapping("/{projectId}")
  public ResponseEntity<Void> deleteProject(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable UUID projectId) {
    projectService.deleteProject(requireCurrentUserId(principal), projectId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{projectId}/transfer-owner")
  public ProjectResponse transferOwner(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable UUID projectId,
      @Valid @RequestBody TransferOwnerRequest request) {
    return projectService.transferOwner(requireCurrentUserId(principal), projectId, request);
  }

  @PostMapping("/{projectId}/invitations")
  public ResponseEntity<ProjectInvitationResponse> createProjectInvitation(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable UUID projectId,
      @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody CreateProjectInvitationRequest request) {
    ProjectInvitationResponse created = projectService.createProjectInvitation(
        requireCurrentUserId(principal),
        projectId,
        request,
        idempotencyKey);
    return ResponseEntity
        .created(URI.create("/api/v1/projects/" + projectId + "/invitations/" + created.id()))
        .body(created);
  }

  @GetMapping("/{projectId}/invitations")
  public List<ProjectInvitationResponse> listProjectInvitations(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable UUID projectId,
      @RequestParam(required = false) Short status) {
    return projectService.listProjectInvitations(requireCurrentUserId(principal), projectId, status);
  }

  @GetMapping("/{projectId}/audit-logs")
  public List<ProjectAuditLogResponse> listProjectAuditLogs(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable UUID projectId,
      @RequestParam(required = false) Integer limit) {
    return projectService.listProjectAuditLogs(requireCurrentUserId(principal), projectId, limit);
  }

  @PostMapping("/invitations/{token}/accept")
  public ProjectMemberResponse acceptProjectInvitation(
      @AuthenticationPrincipal AuthPrincipal principal,
      @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
      @PathVariable String token) {
    return projectService.acceptProjectInvitation(
        requireCurrentUserId(principal),
        requireCurrentUserEmail(principal),
        token,
        idempotencyKey);
  }

  @PutMapping("/{projectId}/members/{userId}")
  public ProjectMemberResponse upsertProjectMember(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable UUID projectId,
      @PathVariable UUID userId,
      @Valid @RequestBody UpsertProjectMemberRequest request) {
    return projectService.upsertProjectMember(requireCurrentUserId(principal), projectId, userId, request);
  }

  @GetMapping("/{projectId}/members")
  public List<ProjectMemberResponse> listProjectMembers(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable UUID projectId,
      @RequestParam(required = false) Short status) {
    return projectService.listProjectMembers(requireCurrentUserId(principal), projectId, status);
  }

  @DeleteMapping("/{projectId}/members/{userId}")
  public ResponseEntity<Void> removeProjectMember(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable UUID projectId,
      @PathVariable UUID userId) {
    projectService.removeProjectMember(requireCurrentUserId(principal), projectId, userId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{projectId}/files")
  public ResponseEntity<FileNodeResponse> createFileNode(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable UUID projectId,
      @Valid @RequestBody CreateFileNodeRequest request) {
    FileNodeResponse created = projectService.createFileNode(requireCurrentUserId(principal), projectId, request);
    return ResponseEntity
        .created(URI.create("/api/v1/projects/" + projectId + "/files/" + created.id()))
        .body(created);
  }

  @GetMapping("/{projectId}/files")
  public List<FileNodeResponse> listFileNodes(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable UUID projectId,
      @RequestParam(required = false) UUID parentId) {
    return projectService.listFileNodes(requireCurrentUserId(principal), projectId, parentId);
  }

  @PatchMapping("/{projectId}/files/{nodeId}")
  public FileNodeResponse updateFileNode(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable UUID projectId,
      @PathVariable UUID nodeId,
      @Valid @RequestBody UpdateFileNodeRequest request) {
    return projectService.updateFileNode(requireCurrentUserId(principal), projectId, nodeId, request);
  }

  @DeleteMapping("/{projectId}/files/{nodeId}")
  public ResponseEntity<Void> deleteFileNode(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable UUID projectId,
      @PathVariable UUID nodeId) {
    projectService.deleteFileNode(requireCurrentUserId(principal), projectId, nodeId);
    return ResponseEntity.noContent().build();
  }

  private UUID requireCurrentUserId(AuthPrincipal principal) {
    if (principal == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized");
    }
    return principal.userId();
  }

  private String requireCurrentUserEmail(AuthPrincipal principal) {
    if (principal == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized");
    }
    String email = principal.email();
    if (email == null || email.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email claim is required");
    }
    return email;
  }
}
