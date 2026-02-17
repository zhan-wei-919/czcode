package com.czcode.projectservice.project.service;

import com.czcode.projectservice.project.dto.CreateFileNodeRequest;
import com.czcode.projectservice.project.dto.ProjectAuditLogResponse;
import com.czcode.projectservice.project.dto.CreateProjectInvitationRequest;
import com.czcode.projectservice.project.dto.CreateProjectRequest;
import com.czcode.projectservice.project.dto.FileNodeResponse;
import com.czcode.projectservice.project.dto.ProjectInvitationResponse;
import com.czcode.projectservice.project.dto.ProjectMemberResponse;
import com.czcode.projectservice.project.dto.ProjectResponse;
import com.czcode.projectservice.project.dto.TransferOwnerRequest;
import com.czcode.projectservice.project.dto.UpdateFileNodeRequest;
import com.czcode.projectservice.project.dto.UpdateProjectRequest;
import com.czcode.projectservice.project.dto.UpsertProjectMemberRequest;
import com.czcode.projectservice.project.entity.BranchProtectionRuleEntity;
import com.czcode.projectservice.project.entity.ProjectAuditLogEntity;
import com.czcode.projectservice.project.entity.ProjectBranchEntity;
import com.czcode.projectservice.project.entity.ProjectEntity;
import com.czcode.projectservice.project.entity.ProjectFileNodeEntity;
import com.czcode.projectservice.project.entity.ProjectInvitationEntity;
import com.czcode.projectservice.project.entity.ProjectMemberEntity;
import com.czcode.projectservice.project.entity.ProjectMemberId;
import com.czcode.projectservice.project.repository.BranchProtectionRuleRepository;
import com.czcode.projectservice.project.repository.ProjectAuditLogRepository;
import com.czcode.projectservice.project.repository.ProjectBranchRepository;
import com.czcode.projectservice.project.repository.ProjectFileNodeRepository;
import com.czcode.projectservice.project.repository.ProjectInvitationRepository;
import com.czcode.projectservice.project.repository.ProjectMemberRepository;
import com.czcode.projectservice.project.repository.ProjectRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectService {

  private static final short PROJECT_STATUS_ACTIVE = 1;
  private static final short PROJECT_STATUS_ARCHIVED = 2;
  private static final short PROJECT_VISIBILITY_PRIVATE = 1;

  private static final short MEMBER_ROLE_OWNER = 1;
  private static final short MEMBER_ROLE_ADMIN = 2;
  private static final short MEMBER_ROLE_MEMBER = 3;
  private static final short MEMBER_ROLE_VIEWER = 4;

  private static final short MEMBER_STATUS_ACTIVE = 1;
  private static final short MEMBER_STATUS_REMOVED = 2;

  private static final short BRANCH_TYPE_MAIN = 1;
  private static final short BRANCH_STATUS_ACTIVE = 1;
  private static final short DEFAULT_BRANCH_MIN_PUSH_ROLE = MEMBER_ROLE_ADMIN;
  private static final short DEFAULT_BRANCH_MIN_MERGE_ROLE = MEMBER_ROLE_ADMIN;
  private static final String DEFAULT_MAIN_BRANCH_NAME = "main";

  private static final short INVITATION_STATUS_PENDING = 1;
  private static final short INVITATION_STATUS_ACCEPTED = 2;
  private static final short INVITATION_STATUS_REVOKED = 3;
  private static final short INVITATION_STATUS_EXPIRED = 4;
  private static final int DEFAULT_INVITATION_EXPIRE_HOURS = 72;
  private static final int DEFAULT_AUDIT_LOG_LIMIT = 50;
  private static final int MAX_AUDIT_LOG_LIMIT = 200;

  private static final String AUDIT_ACTION_OWNER_TRANSFERRED = "project.owner_transferred";
  private static final String AUDIT_ACTION_MEMBER_UPSERTED = "project.member_upserted";
  private static final String AUDIT_ACTION_MEMBER_REMOVED = "project.member_removed";
  private static final String AUDIT_ACTION_INVITATION_CREATED = "project.invitation_created";
  private static final String AUDIT_ACTION_INVITATION_ACCEPTED = "project.invitation_accepted";
  private static final String IDEMPOTENCY_SCOPE_INVITATION_CREATE = "project.invitation.create";
  private static final String IDEMPOTENCY_SCOPE_INVITATION_ACCEPT = "project.invitation.accept";

  private final ProjectRepository projectRepository;
  private final ProjectMemberRepository projectMemberRepository;
  private final ProjectFileNodeRepository projectFileNodeRepository;
  private final ProjectBranchRepository projectBranchRepository;
  private final BranchProtectionRuleRepository branchProtectionRuleRepository;
  private final ProjectInvitationRepository projectInvitationRepository;
  private final ProjectAuditLogRepository projectAuditLogRepository;
  private final ProjectAuditLogService projectAuditLogService;
  private final ProjectIdempotencyService projectIdempotencyService;

  private enum Permission {
    PROJECT_READ,
    PROJECT_UPDATE,
    PROJECT_DELETE,
    PROJECT_TRANSFER_OWNER,
    MEMBER_READ,
    MEMBER_WRITE,
    MEMBER_ROLE_GRANT,
    AUDIT_READ,
    FILE_READ,
    FILE_WRITE
  }

  public ProjectService(
      ProjectRepository projectRepository,
      ProjectMemberRepository projectMemberRepository,
      ProjectFileNodeRepository projectFileNodeRepository,
      ProjectBranchRepository projectBranchRepository,
      BranchProtectionRuleRepository branchProtectionRuleRepository,
      ProjectInvitationRepository projectInvitationRepository,
      ProjectAuditLogRepository projectAuditLogRepository,
      ProjectAuditLogService projectAuditLogService,
      ProjectIdempotencyService projectIdempotencyService) {
    this.projectRepository = projectRepository;
    this.projectMemberRepository = projectMemberRepository;
    this.projectFileNodeRepository = projectFileNodeRepository;
    this.projectBranchRepository = projectBranchRepository;
    this.branchProtectionRuleRepository = branchProtectionRuleRepository;
    this.projectInvitationRepository = projectInvitationRepository;
    this.projectAuditLogRepository = projectAuditLogRepository;
    this.projectAuditLogService = projectAuditLogService;
    this.projectIdempotencyService = projectIdempotencyService;
  }

  @Transactional
  public ProjectResponse createProject(UUID requesterUserId, CreateProjectRequest request) {
    if (!request.ownerUserId().equals(requesterUserId)) {
      throw forbidden("owner user id must be current user");
    }

    String projectKey = normalizeProjectKey(request.projectKey());
    if (projectRepository.existsByProjectKeyAndDeletedAtIsNull(projectKey)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "project key already exists");
    }

    ProjectEntity project = new ProjectEntity();
    project.setProjectKey(projectKey);
    project.setName(normalizeName(request.name(), 128, "project name"));
    project.setDescription(normalizeDescription(request.description()));
    project.setOwnerUserId(requesterUserId);
    project.setVisibility(request.visibility() == null ? PROJECT_VISIBILITY_PRIVATE : request.visibility());
    project.setStatus(PROJECT_STATUS_ACTIVE);
    ProjectEntity saved = projectRepository.save(project);

    ProjectMemberEntity ownerMember = new ProjectMemberEntity();
    ownerMember.setId(new ProjectMemberId(saved.getId(), saved.getOwnerUserId()));
    ownerMember.setRole(MEMBER_ROLE_OWNER);
    ownerMember.setStatus(MEMBER_STATUS_ACTIVE);
    ownerMember.setInviterUserId(saved.getOwnerUserId());
    projectMemberRepository.save(ownerMember);
    initializeDefaultBranchAndProtection(saved.getId(), requesterUserId);

    return toProjectResponse(saved);
  }

  @Transactional(readOnly = true)
  public ProjectResponse getProject(UUID requesterUserId, UUID projectId) {
    requirePermission(projectId, requesterUserId, Permission.PROJECT_READ);
    return toProjectResponse(findActiveProject(projectId));
  }

  @Transactional(readOnly = true)
  public List<ProjectResponse> listProjects(UUID requesterUserId) {
    List<ProjectMemberEntity> memberships = projectMemberRepository.findByIdUserIdAndStatusOrderByUpdatedAtDesc(
        requesterUserId,
        MEMBER_STATUS_ACTIVE);
    if (memberships.isEmpty()) {
      return List.of();
    }

    List<UUID> projectIds = memberships.stream().map(entity -> entity.getId().getProjectId()).toList();
    return projectRepository.findAllById(projectIds)
        .stream()
        .filter(entity -> entity.getDeletedAt() == null)
        .sorted(Comparator.comparing(ProjectEntity::getUpdatedAt).reversed())
        .map(this::toProjectResponse)
        .toList();
  }

  @Transactional
  public ProjectResponse updateProject(UUID requesterUserId, UUID projectId, UpdateProjectRequest request) {
    requirePermission(projectId, requesterUserId, Permission.PROJECT_UPDATE);
    ProjectEntity project = findActiveProject(projectId);

    if (request.name() != null) {
      project.setName(normalizeName(request.name(), 128, "project name"));
    }
    if (request.description() != null) {
      project.setDescription(normalizeDescription(request.description()));
    }
    if (request.visibility() != null) {
      validateVisibility(request.visibility());
      project.setVisibility(request.visibility());
    }
    if (request.status() != null) {
      validateProjectStatus(request.status());
      project.setStatus(request.status());
    }

    return toProjectResponse(projectRepository.save(project));
  }

  @Transactional
  public void deleteProject(UUID requesterUserId, UUID projectId) {
    requirePermission(projectId, requesterUserId, Permission.PROJECT_DELETE);
    ProjectEntity project = findActiveProject(projectId);
    Instant now = Instant.now();

    project.setDeletedAt(now);
    project.setStatus(PROJECT_STATUS_ARCHIVED);
    projectRepository.save(project);

    List<ProjectMemberEntity> members = projectMemberRepository.findByIdProjectIdOrderByJoinedAtAsc(projectId);
    for (ProjectMemberEntity member : members) {
      member.setStatus(MEMBER_STATUS_REMOVED);
    }
    projectMemberRepository.saveAll(members);

    List<ProjectFileNodeEntity> nodes = projectFileNodeRepository.findByProjectIdAndDeletedAtIsNullOrderByPathAsc(
        projectId);
    for (ProjectFileNodeEntity node : nodes) {
      node.setDeletedAt(now);
    }
    projectFileNodeRepository.saveAll(nodes);
  }

  @Transactional
  public ProjectResponse transferOwner(UUID requesterUserId, UUID projectId, TransferOwnerRequest request) {
    ProjectMemberEntity operator = requirePermission(projectId, requesterUserId, Permission.PROJECT_TRANSFER_OWNER);
    ProjectEntity project = findActiveProject(projectId);

    UUID targetUserId = request.targetUserId();
    if (targetUserId.equals(requesterUserId)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "target owner must be different from current owner");
    }

    ProjectMemberEntity target = projectMemberRepository
        .findByIdProjectIdAndIdUserIdAndStatus(projectId, targetUserId, MEMBER_STATUS_ACTIVE)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "target member not found"));

    if (target.getRole() == MEMBER_ROLE_OWNER) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "target member is already owner");
    }

    boolean hasOtherAdmin = projectMemberRepository.findByIdProjectIdAndStatusOrderByJoinedAtAsc(
            projectId,
            MEMBER_STATUS_ACTIVE)
        .stream()
        .anyMatch(member -> member.getRole() == MEMBER_ROLE_ADMIN
            && !member.getId().getUserId().equals(requesterUserId)
            && !member.getId().getUserId().equals(targetUserId));

    operator.setRole(hasOtherAdmin ? MEMBER_ROLE_MEMBER : MEMBER_ROLE_ADMIN);
    target.setRole(MEMBER_ROLE_OWNER);
    project.setOwnerUserId(targetUserId);

    projectMemberRepository.saveAll(List.of(operator, target));
    ProjectEntity savedProject = projectRepository.save(project);

    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("fromUserId", requesterUserId.toString());
    detail.put("toUserId", targetUserId.toString());
    detail.put("oldOwnerNewRole", operator.getRole());
    projectAuditLogService.record(
        projectId,
        requesterUserId,
        AUDIT_ACTION_OWNER_TRANSFERRED,
        "project",
        projectId,
        detail);

    return toProjectResponse(savedProject);
  }

  @Transactional
  public ProjectInvitationResponse createProjectInvitation(
      UUID requesterUserId,
      UUID projectId,
      CreateProjectInvitationRequest request) {
    return createProjectInvitation(requesterUserId, projectId, request, null);
  }

  @Transactional
  public ProjectInvitationResponse createProjectInvitation(
      UUID requesterUserId,
      UUID projectId,
      CreateProjectInvitationRequest request,
      String idempotencyKey) {
    return projectIdempotencyService.execute(
        projectId,
        requesterUserId,
        IDEMPOTENCY_SCOPE_INVITATION_CREATE,
        idempotencyKey,
        request,
        ProjectInvitationResponse.class,
        () -> createProjectInvitationInternal(requesterUserId, projectId, request));
  }

  private ProjectInvitationResponse createProjectInvitationInternal(
      UUID requesterUserId,
      UUID projectId,
      CreateProjectInvitationRequest request) {
    requirePermission(projectId, requesterUserId, Permission.MEMBER_WRITE);
    validateInvitableRole(request.role());

    String inviteeEmail = normalizeEmail(request.inviteeEmail());
    if (projectInvitationRepository.existsByProjectIdAndInviteeEmailAndStatus(
        projectId,
        inviteeEmail,
        INVITATION_STATUS_PENDING)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "pending invitation already exists for this email");
    }

    int expireHours = request.expireHours() == null ? DEFAULT_INVITATION_EXPIRE_HOURS : request.expireHours();
    Instant expiredAt = Instant.now().plusSeconds((long) expireHours * 3600L);

    ProjectInvitationEntity invitation = new ProjectInvitationEntity();
    invitation.setProjectId(projectId);
    invitation.setInviteeEmail(inviteeEmail);
    invitation.setRole(request.role());
    invitation.setToken(generateInvitationToken());
    invitation.setStatus(INVITATION_STATUS_PENDING);
    invitation.setExpiredAt(expiredAt);
    invitation.setCreatedBy(requesterUserId);
    ProjectInvitationEntity savedInvitation = projectInvitationRepository.save(invitation);

    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("inviteeEmail", savedInvitation.getInviteeEmail());
    detail.put("role", savedInvitation.getRole());
    detail.put("expiredAt", savedInvitation.getExpiredAt().toString());
    projectAuditLogService.record(
        projectId,
        requesterUserId,
        AUDIT_ACTION_INVITATION_CREATED,
        "project_invitation",
        savedInvitation.getId(),
        detail);

    return toProjectInvitationResponse(savedInvitation);
  }

  @Transactional(readOnly = true)
  public List<ProjectInvitationResponse> listProjectInvitations(UUID requesterUserId, UUID projectId, Short status) {
    requirePermission(projectId, requesterUserId, Permission.MEMBER_READ);
    if (status != null) {
      validateInvitationStatus(status);
    }
    List<ProjectInvitationEntity> invitations = status == null
        ? projectInvitationRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
        : projectInvitationRepository.findByProjectIdAndStatusOrderByCreatedAtDesc(projectId, status);
    return invitations.stream().map(this::toProjectInvitationResponse).toList();
  }

  @Transactional(readOnly = true)
  public List<ProjectAuditLogResponse> listProjectAuditLogs(UUID requesterUserId, UUID projectId, Integer limit) {
    requirePermission(projectId, requesterUserId, Permission.AUDIT_READ);
    int pageSize = normalizeAuditLogLimit(limit);
    return projectAuditLogRepository
        .findByProjectIdOrderByCreatedAtDesc(projectId, PageRequest.of(0, pageSize))
        .stream()
        .map(this::toProjectAuditLogResponse)
        .toList();
  }

  @Transactional
  public ProjectMemberResponse acceptProjectInvitation(UUID requesterUserId, String requesterEmail, String token) {
    return acceptProjectInvitation(requesterUserId, requesterEmail, token, null);
  }

  @Transactional
  public ProjectMemberResponse acceptProjectInvitation(
      UUID requesterUserId,
      String requesterEmail,
      String token,
      String idempotencyKey) {
    String normalizedEmail = normalizeEmail(requesterEmail);
    ProjectInvitationEntity invitation = projectInvitationRepository.findByToken(token)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "invitation not found"));

    return projectIdempotencyService.execute(
        invitation.getProjectId(),
        requesterUserId,
        IDEMPOTENCY_SCOPE_INVITATION_ACCEPT,
        idempotencyKey,
        Map.of("token", token, "email", normalizedEmail),
        ProjectMemberResponse.class,
        () -> acceptProjectInvitationInternal(requesterUserId, normalizedEmail, invitation));
  }

  private ProjectMemberResponse acceptProjectInvitationInternal(
      UUID requesterUserId,
      String normalizedEmail,
      ProjectInvitationEntity invitation) {
    if (invitation.getStatus() != INVITATION_STATUS_PENDING) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invitation is not pending");
    }
    if (invitation.getExpiredAt().isBefore(Instant.now())) {
      invitation.setStatus(INVITATION_STATUS_EXPIRED);
      projectInvitationRepository.save(invitation);
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invitation has expired");
    }
    if (!invitation.getInviteeEmail().equals(normalizedEmail)) {
      throw forbidden("invitation email does not match current user");
    }

    findActiveProject(invitation.getProjectId());

    ProjectMemberEntity member = projectMemberRepository.findByIdProjectIdAndIdUserId(
            invitation.getProjectId(),
            requesterUserId)
        .orElse(null);
    if (member == null) {
      member = new ProjectMemberEntity();
      member.setId(new ProjectMemberId(invitation.getProjectId(), requesterUserId));
      member.setJoinedAt(Instant.now());
    }

    if (member.getRole() == MEMBER_ROLE_OWNER) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "owner cannot accept invitation");
    }

    member.setRole(invitation.getRole());
    member.setStatus(MEMBER_STATUS_ACTIVE);
    member.setInviterUserId(invitation.getCreatedBy());
    ProjectMemberEntity saved = projectMemberRepository.save(member);

    invitation.setStatus(INVITATION_STATUS_ACCEPTED);
    invitation.setAcceptedAt(Instant.now());
    projectInvitationRepository.save(invitation);

    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("inviteeEmail", invitation.getInviteeEmail());
    detail.put("role", invitation.getRole());
    detail.put("memberUserId", requesterUserId.toString());
    projectAuditLogService.record(
        invitation.getProjectId(),
        requesterUserId,
        AUDIT_ACTION_INVITATION_ACCEPTED,
        "project_invitation",
        invitation.getId(),
        detail);

    return toProjectMemberResponse(saved);
  }

  @Transactional
  public ProjectMemberResponse upsertProjectMember(
      UUID requesterUserId,
      UUID projectId,
      UUID userId,
      UpsertProjectMemberRequest request) {
    ProjectMemberEntity operator = requirePermission(projectId, requesterUserId, Permission.MEMBER_WRITE);
    if (!hasPermission(operator.getRole(), Permission.MEMBER_ROLE_GRANT)) {
      throw forbidden("no permission to grant role");
    }

    validateMemberRole(request.role());
    short targetStatus = request.status() == null ? MEMBER_STATUS_ACTIVE : request.status();
    validateMemberStatus(targetStatus);

    ProjectMemberEntity existing = projectMemberRepository.findByIdProjectIdAndIdUserId(projectId, userId)
        .orElse(null);

    if (operator.getRole() != MEMBER_ROLE_OWNER && request.role() == MEMBER_ROLE_OWNER) {
      throw forbidden("only owner can grant owner role");
    }
    if (existing != null && existing.getRole() == MEMBER_ROLE_OWNER && operator.getRole() != MEMBER_ROLE_OWNER) {
      throw forbidden("only owner can modify owner member");
    }

    Short beforeRole = existing == null ? null : existing.getRole();
    Short beforeStatus = existing == null ? null : existing.getStatus();

    ProjectMemberEntity entity = existing;
    if (entity == null) {
      entity = new ProjectMemberEntity();
      entity.setId(new ProjectMemberId(projectId, userId));
      entity.setJoinedAt(Instant.now());
    }

    entity.setRole(request.role());
    entity.setInviterUserId(request.inviterUserId() == null ? requesterUserId : request.inviterUserId());
    entity.setStatus(targetStatus);
    ProjectMemberEntity saved = projectMemberRepository.save(entity);

    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("targetUserId", userId.toString());
    detail.put("beforeRole", beforeRole);
    detail.put("afterRole", saved.getRole());
    detail.put("beforeStatus", beforeStatus);
    detail.put("afterStatus", saved.getStatus());
    detail.put("operatorRole", operator.getRole());
    projectAuditLogService.record(
        projectId,
        requesterUserId,
        AUDIT_ACTION_MEMBER_UPSERTED,
        "project_member",
        userId,
        detail);

    return toProjectMemberResponse(saved);
  }

  @Transactional(readOnly = true)
  public List<ProjectMemberResponse> listProjectMembers(UUID requesterUserId, UUID projectId, Short status) {
    requirePermission(projectId, requesterUserId, Permission.MEMBER_READ);
    if (status != null) {
      validateMemberStatus(status);
    }
    List<ProjectMemberEntity> entities = status == null
        ? projectMemberRepository.findByIdProjectIdOrderByJoinedAtAsc(projectId)
        : projectMemberRepository.findByIdProjectIdAndStatusOrderByJoinedAtAsc(projectId, status);
    return entities.stream().map(this::toProjectMemberResponse).toList();
  }

  @Transactional
  public void removeProjectMember(UUID requesterUserId, UUID projectId, UUID userId) {
    ProjectMemberEntity operator = requirePermission(projectId, requesterUserId, Permission.MEMBER_WRITE);
    ProjectMemberEntity target = projectMemberRepository.findByIdProjectIdAndIdUserId(projectId, userId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "member not found"));

    if (target.getRole() == MEMBER_ROLE_OWNER) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "owner member cannot be removed directly");
    }
    if (operator.getRole() != MEMBER_ROLE_OWNER && target.getRole() == MEMBER_ROLE_ADMIN) {
      throw forbidden("only owner can remove admin member");
    }

    target.setStatus(MEMBER_STATUS_REMOVED);
    projectMemberRepository.save(target);

    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("targetUserId", userId.toString());
    detail.put("targetRole", target.getRole());
    detail.put("targetNewStatus", target.getStatus());
    detail.put("operatorRole", operator.getRole());
    projectAuditLogService.record(
        projectId,
        requesterUserId,
        AUDIT_ACTION_MEMBER_REMOVED,
        "project_member",
        userId,
        detail);
  }

  @Transactional
  public FileNodeResponse createFileNode(UUID requesterUserId, UUID projectId, CreateFileNodeRequest request) {
    requirePermission(projectId, requesterUserId, Permission.FILE_WRITE);

    String nodeName = normalizeFileNodeName(request.name());
    validateNodeDocRelation(request.isDirectory(), request.collabDocId());

    UUID parentId = request.parentId();
    String parentPath = "";
    if (parentId != null) {
      ProjectFileNodeEntity parent = findActiveNode(projectId, parentId);
      if (!parent.isDirectory()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "parent node is not a directory");
      }
      parentPath = parent.getPath();
    }
    String path = buildPath(parentPath, nodeName);

    if (projectFileNodeRepository.existsByProjectIdAndPathAndDeletedAtIsNull(projectId, path)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "file node path already exists");
    }

    ProjectFileNodeEntity node = new ProjectFileNodeEntity();
    node.setProjectId(projectId);
    node.setParentId(parentId);
    node.setName(nodeName);
    node.setDirectory(request.isDirectory());
    node.setCollabDocId(request.collabDocId());
    node.setPath(path);
    return toFileNodeResponse(projectFileNodeRepository.save(node));
  }

  @Transactional(readOnly = true)
  public List<FileNodeResponse> listFileNodes(UUID requesterUserId, UUID projectId, UUID parentId) {
    requirePermission(projectId, requesterUserId, Permission.FILE_READ);
    List<ProjectFileNodeEntity> entities = parentId == null
        ? projectFileNodeRepository.findByProjectIdAndParentIdIsNullAndDeletedAtIsNullOrderByDirectoryDescNameAsc(
            projectId)
        : projectFileNodeRepository.findByProjectIdAndParentIdAndDeletedAtIsNullOrderByDirectoryDescNameAsc(
            projectId,
            parentId);
    return entities.stream().map(this::toFileNodeResponse).toList();
  }

  @Transactional
  public FileNodeResponse updateFileNode(
      UUID requesterUserId,
      UUID projectId,
      UUID nodeId,
      UpdateFileNodeRequest request) {
    requirePermission(projectId, requesterUserId, Permission.FILE_WRITE);
    ProjectFileNodeEntity node = findActiveNode(projectId, nodeId);
    String oldPath = node.getPath();

    UUID targetParentId = node.getParentId();
    if (request.parentId() != null && !request.parentId().equals(node.getParentId())) {
      if (request.parentId().equals(node.getId())) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cannot move node to itself");
      }
      ProjectFileNodeEntity parent = findActiveNode(projectId, request.parentId());
      if (!parent.isDirectory()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "target parent is not a directory");
      }
      if (node.isDirectory() && parent.getPath().startsWith(oldPath + "/")) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cannot move directory to its descendant");
      }
      targetParentId = parent.getId();
    }

    String targetName = request.name() == null ? node.getName() : normalizeFileNodeName(request.name());
    UUID targetCollabDocId = request.collabDocId() == null ? node.getCollabDocId() : request.collabDocId();
    validateNodeDocRelation(node.isDirectory(), targetCollabDocId);

    String parentPath = "";
    if (targetParentId != null) {
      ProjectFileNodeEntity parent = findActiveNode(projectId, targetParentId);
      parentPath = parent.getPath();
    }
    String newPath = buildPath(parentPath, targetName);

    if (!newPath.equals(oldPath) && projectFileNodeRepository.existsByProjectIdAndPathAndDeletedAtIsNull(
        projectId,
        newPath)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "target path already exists");
    }

    node.setParentId(targetParentId);
    node.setName(targetName);
    node.setCollabDocId(targetCollabDocId);
    node.setPath(newPath);
    ProjectFileNodeEntity saved = projectFileNodeRepository.save(node);

    if (saved.isDirectory() && !newPath.equals(oldPath)) {
      List<ProjectFileNodeEntity> descendants = projectFileNodeRepository
          .findByProjectIdAndPathStartingWithAndDeletedAtIsNull(projectId, oldPath + "/");
      List<ProjectFileNodeEntity> dirty = new ArrayList<>();
      for (ProjectFileNodeEntity descendant : descendants) {
        String suffix = descendant.getPath().substring(oldPath.length());
        descendant.setPath(newPath + suffix);
        dirty.add(descendant);
      }
      projectFileNodeRepository.saveAll(dirty);
    }

    return toFileNodeResponse(saved);
  }

  @Transactional
  public void deleteFileNode(UUID requesterUserId, UUID projectId, UUID nodeId) {
    requirePermission(projectId, requesterUserId, Permission.FILE_WRITE);
    ProjectFileNodeEntity node = findActiveNode(projectId, nodeId);
    Instant now = Instant.now();
    String path = node.getPath();

    List<ProjectFileNodeEntity> candidates = projectFileNodeRepository
        .findByProjectIdAndPathStartingWithAndDeletedAtIsNull(projectId, path);
    for (ProjectFileNodeEntity candidate : candidates) {
      String currentPath = candidate.getPath();
      if (currentPath.equals(path) || currentPath.startsWith(path + "/")) {
        candidate.setDeletedAt(now);
      }
    }
    projectFileNodeRepository.saveAll(candidates);
  }

  private ProjectMemberEntity requirePermission(UUID projectId, UUID userId, Permission permission) {
    ProjectMemberEntity member = requireActiveMember(projectId, userId);
    if (!hasPermission(member.getRole(), permission)) {
      throw forbidden("insufficient project permission");
    }
    return member;
  }

  private ProjectMemberEntity requireActiveMember(UUID projectId, UUID userId) {
    findActiveProject(projectId);
    return projectMemberRepository.findByIdProjectIdAndIdUserIdAndStatus(projectId, userId, MEMBER_STATUS_ACTIVE)
        .orElseThrow(() -> forbidden("project access denied"));
  }

  private boolean hasPermission(short role, Permission permission) {
    return switch (role) {
      case MEMBER_ROLE_OWNER -> true;
      case MEMBER_ROLE_ADMIN -> permission != Permission.PROJECT_DELETE
          && permission != Permission.PROJECT_TRANSFER_OWNER;
      case MEMBER_ROLE_MEMBER -> permission == Permission.PROJECT_READ
          || permission == Permission.MEMBER_READ
          || permission == Permission.FILE_READ
          || permission == Permission.FILE_WRITE;
      case MEMBER_ROLE_VIEWER -> permission == Permission.PROJECT_READ
          || permission == Permission.MEMBER_READ
          || permission == Permission.FILE_READ;
      default -> false;
    };
  }

  private ProjectEntity findActiveProject(UUID projectId) {
    return projectRepository.findByIdAndDeletedAtIsNull(projectId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "project not found"));
  }

  private ProjectFileNodeEntity findActiveNode(UUID projectId, UUID nodeId) {
    return projectFileNodeRepository.findByIdAndProjectIdAndDeletedAtIsNull(nodeId, projectId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "file node not found"));
  }

  private String normalizeProjectKey(String projectKey) {
    String normalized = projectKey.trim().toUpperCase(Locale.ROOT);
    if (!normalized.matches("[A-Z0-9_\\-]+")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid project key");
    }
    return normalized;
  }

  private String normalizeName(String value, int maxLength, String fieldName) {
    String normalized = value == null ? "" : value.trim();
    if (normalized.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " cannot be blank");
    }
    if (normalized.length() > maxLength) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is too long");
    }
    return normalized;
  }

  private String normalizeDescription(String description) {
    if (description == null) {
      return null;
    }
    String normalized = description.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  private String normalizeEmail(String email) {
    String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    if (normalized.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email cannot be blank");
    }
    if (normalized.length() > 128) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is too long");
    }
    return normalized;
  }

  private int normalizeAuditLogLimit(Integer limit) {
    if (limit == null) {
      return DEFAULT_AUDIT_LOG_LIMIT;
    }
    if (limit < 1 || limit > MAX_AUDIT_LOG_LIMIT) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "limit must be between 1 and " + MAX_AUDIT_LOG_LIMIT);
    }
    return limit;
  }

  private void validateVisibility(short visibility) {
    if (visibility < 1 || visibility > 3) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid visibility");
    }
  }

  private void validateProjectStatus(short status) {
    if (status != PROJECT_STATUS_ACTIVE && status != PROJECT_STATUS_ARCHIVED) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid project status");
    }
  }

  private void validateMemberRole(short role) {
    if (role < MEMBER_ROLE_OWNER || role > MEMBER_ROLE_VIEWER) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid member role");
    }
  }

  private void validateInvitableRole(short role) {
    if (role < MEMBER_ROLE_ADMIN || role > MEMBER_ROLE_VIEWER) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid invitation role");
    }
  }

  private void validateMemberStatus(short status) {
    if (status != MEMBER_STATUS_ACTIVE && status != MEMBER_STATUS_REMOVED) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid member status");
    }
  }

  private void validateInvitationStatus(short status) {
    if (status != INVITATION_STATUS_PENDING
        && status != INVITATION_STATUS_ACCEPTED
        && status != INVITATION_STATUS_REVOKED
        && status != INVITATION_STATUS_EXPIRED) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid invitation status");
    }
  }

  private void validateNodeDocRelation(boolean isDirectory, UUID collabDocId) {
    if (isDirectory && collabDocId != null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "directory must not bind collab document");
    }
    if (!isDirectory && collabDocId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file must bind collab document");
    }
  }

  private String normalizeFileNodeName(String name) {
    String normalized = normalizeName(name, 128, "file node name");
    if (normalized.contains("/") || normalized.contains("\\")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file node name cannot contain slashes");
    }
    if (".".equals(normalized) || "..".equals(normalized)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid file node name");
    }
    return normalized;
  }

  private String buildPath(String parentPath, String name) {
    if (parentPath == null || parentPath.isBlank()) {
      return "/" + name;
    }
    return parentPath + "/" + name;
  }

  private String generateInvitationToken() {
    return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
  }

  private void initializeDefaultBranchAndProtection(UUID projectId, UUID createdBy) {
    ProjectBranchEntity mainBranch = new ProjectBranchEntity();
    mainBranch.setProjectId(projectId);
    mainBranch.setName(DEFAULT_MAIN_BRANCH_NAME);
    mainBranch.setBranchType(BRANCH_TYPE_MAIN);
    mainBranch.setStatus(BRANCH_STATUS_ACTIVE);
    mainBranch.setCreatedBy(createdBy);
    projectBranchRepository.save(mainBranch);

    BranchProtectionRuleEntity mainRule = new BranchProtectionRuleEntity();
    mainRule.setProjectId(projectId);
    mainRule.setBranchPattern(DEFAULT_MAIN_BRANCH_NAME);
    mainRule.setMinPushRole(DEFAULT_BRANCH_MIN_PUSH_ROLE);
    mainRule.setMinMergeRole(DEFAULT_BRANCH_MIN_MERGE_ROLE);
    mainRule.setRequirePr(true);
    mainRule.setAllowForcePush(false);
    mainRule.setAllowDeleteBranch(false);
    mainRule.setCreatedBy(createdBy);
    branchProtectionRuleRepository.save(mainRule);
  }

  private ResponseStatusException forbidden(String message) {
    return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
  }

  private ProjectResponse toProjectResponse(ProjectEntity entity) {
    return new ProjectResponse(
        entity.getId(),
        entity.getProjectKey(),
        entity.getName(),
        entity.getDescription(),
        entity.getOwnerUserId(),
        entity.getVisibility(),
        entity.getStatus(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }

  private ProjectMemberResponse toProjectMemberResponse(ProjectMemberEntity entity) {
    return new ProjectMemberResponse(
        entity.getId().getProjectId(),
        entity.getId().getUserId(),
        entity.getRole(),
        entity.getStatus(),
        entity.getInviterUserId(),
        entity.getJoinedAt(),
        entity.getUpdatedAt());
  }

  private FileNodeResponse toFileNodeResponse(ProjectFileNodeEntity entity) {
    return new FileNodeResponse(
        entity.getId(),
        entity.getProjectId(),
        entity.getParentId(),
        entity.getName(),
        entity.isDirectory(),
        entity.getCollabDocId(),
        entity.getPath(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }

  private ProjectInvitationResponse toProjectInvitationResponse(ProjectInvitationEntity entity) {
    return new ProjectInvitationResponse(
        entity.getId(),
        entity.getProjectId(),
        entity.getInviteeEmail(),
        entity.getRole(),
        entity.getToken(),
        entity.getStatus(),
        entity.getExpiredAt(),
        entity.getAcceptedAt(),
        entity.getCreatedBy(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }

  private ProjectAuditLogResponse toProjectAuditLogResponse(ProjectAuditLogEntity entity) {
    return new ProjectAuditLogResponse(
        entity.getId(),
        entity.getProjectId(),
        entity.getActorUserId(),
        entity.getAction(),
        entity.getTargetType(),
        entity.getTargetId(),
        entity.getDetailJson(),
        entity.getCreatedAt());
  }
}
