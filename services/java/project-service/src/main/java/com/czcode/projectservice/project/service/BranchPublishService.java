package com.czcode.projectservice.project.service;

import com.czcode.projectservice.project.dto.BranchCheckpointResponse;
import com.czcode.projectservice.project.dto.BranchProtectionRuleResponse;
import com.czcode.projectservice.project.dto.CreateBranchCheckpointRequest;
import com.czcode.projectservice.project.dto.CreateBranchProtectionRuleRequest;
import com.czcode.projectservice.project.dto.CreateProjectBranchRequest;
import com.czcode.projectservice.project.dto.CreatePublishRecordRequest;
import com.czcode.projectservice.project.dto.ProjectBranchResponse;
import com.czcode.projectservice.project.dto.PublishRecordResponse;
import com.czcode.projectservice.project.dto.UpdateBranchProtectionRuleRequest;
import com.czcode.projectservice.project.dto.UpdateProjectBranchRequest;
import com.czcode.projectservice.project.entity.BranchCheckpointEntity;
import com.czcode.projectservice.project.entity.BranchProtectionRuleEntity;
import com.czcode.projectservice.project.entity.ProjectBranchEntity;
import com.czcode.projectservice.project.entity.ProjectEntity;
import com.czcode.projectservice.project.entity.ProjectMemberEntity;
import com.czcode.projectservice.project.entity.PublishRecordEntity;
import com.czcode.projectservice.project.repository.BranchCheckpointRepository;
import com.czcode.projectservice.project.repository.BranchProtectionRuleRepository;
import com.czcode.projectservice.project.repository.ProjectBranchRepository;
import com.czcode.projectservice.project.repository.ProjectMemberRepository;
import com.czcode.projectservice.project.repository.ProjectRepository;
import com.czcode.projectservice.project.repository.PublishRecordRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BranchPublishService {

  private static final short MEMBER_ROLE_OWNER = 1;
  private static final short MEMBER_ROLE_ADMIN = 2;
  private static final short MEMBER_ROLE_MEMBER = 3;
  private static final short MEMBER_ROLE_VIEWER = 4;

  private static final short MEMBER_STATUS_ACTIVE = 1;

  private static final short BRANCH_TYPE_MAIN = 1;
  private static final short BRANCH_TYPE_FEATURE = 2;
  private static final short BRANCH_TYPE_BUGFIX = 3;
  private static final short BRANCH_TYPE_RELEASE = 4;
  private static final short BRANCH_TYPE_HOTFIX = 5;

  private static final short BRANCH_STATUS_ACTIVE = 1;
  private static final short BRANCH_STATUS_MERGED = 2;
  private static final short BRANCH_STATUS_ARCHIVED = 3;

  private static final short PUBLISH_STATUS_PENDING = 1;
  private static final short PUBLISH_STATUS_SUCCESS = 2;
  private static final short PUBLISH_STATUS_FAILED = 3;

  private static final short DEFAULT_MIN_PUSH_ROLE = MEMBER_ROLE_MEMBER;
  private static final short DEFAULT_MIN_MERGE_ROLE = MEMBER_ROLE_ADMIN;
  private static final String AUDIT_ACTION_PUBLISH_CREATED = "project.publish_created";
  private static final String IDEMPOTENCY_SCOPE_PUBLISH_CREATE = "project.publish.create";

  private final ProjectRepository projectRepository;
  private final ProjectMemberRepository projectMemberRepository;
  private final ProjectBranchRepository projectBranchRepository;
  private final BranchProtectionRuleRepository branchProtectionRuleRepository;
  private final BranchCheckpointRepository branchCheckpointRepository;
  private final PublishRecordRepository publishRecordRepository;
  private final ProjectAuditLogService projectAuditLogService;
  private final ProjectIdempotencyService projectIdempotencyService;

  private enum Permission {
    BRANCH_READ,
    BRANCH_CREATE,
    BRANCH_UPDATE,
    BRANCH_DELETE,
    RULE_READ,
    RULE_WRITE,
    CHECKPOINT_READ,
    CHECKPOINT_CREATE,
    PUBLISH_READ,
    PUBLISH_EXECUTE
  }

  public BranchPublishService(
      ProjectRepository projectRepository,
      ProjectMemberRepository projectMemberRepository,
      ProjectBranchRepository projectBranchRepository,
      BranchProtectionRuleRepository branchProtectionRuleRepository,
      BranchCheckpointRepository branchCheckpointRepository,
      PublishRecordRepository publishRecordRepository,
      ProjectAuditLogService projectAuditLogService,
      ProjectIdempotencyService projectIdempotencyService) {
    this.projectRepository = projectRepository;
    this.projectMemberRepository = projectMemberRepository;
    this.projectBranchRepository = projectBranchRepository;
    this.branchProtectionRuleRepository = branchProtectionRuleRepository;
    this.branchCheckpointRepository = branchCheckpointRepository;
    this.publishRecordRepository = publishRecordRepository;
    this.projectAuditLogService = projectAuditLogService;
    this.projectIdempotencyService = projectIdempotencyService;
  }

  @Transactional
  public ProjectBranchResponse createBranch(
      UUID requesterUserId,
      UUID projectId,
      CreateProjectBranchRequest request) {
    requirePermission(projectId, requesterUserId, Permission.BRANCH_CREATE);

    String branchName = normalizeBranchName(request.name());
    if (projectBranchRepository.existsByProjectIdAndNameAndDeletedAtIsNull(projectId, branchName)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "branch name already exists");
    }

    short branchType = request.branchType() == null ? inferDefaultBranchType(branchName) : request.branchType();
    validateBranchType(branchType);

    ProjectBranchEntity basedOnBranch = null;
    if (request.basedOnBranchId() != null) {
      basedOnBranch = findActiveBranch(projectId, request.basedOnBranchId());
      if (basedOnBranch.getStatus() != BRANCH_STATUS_ACTIVE) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "based-on branch is not active");
      }
    }

    ProjectBranchEntity branch = new ProjectBranchEntity();
    branch.setProjectId(projectId);
    branch.setName(branchName);
    branch.setBranchType(branchType);
    branch.setBasedOnBranchId(request.basedOnBranchId());
    branch.setHeadCheckpointId(basedOnBranch == null ? null : basedOnBranch.getHeadCheckpointId());
    branch.setStatus(BRANCH_STATUS_ACTIVE);
    branch.setCreatedBy(requesterUserId);

    return toProjectBranchResponse(projectBranchRepository.save(branch));
  }

  @Transactional(readOnly = true)
  public ProjectBranchResponse getBranch(UUID requesterUserId, UUID projectId, UUID branchId) {
    requirePermission(projectId, requesterUserId, Permission.BRANCH_READ);
    return toProjectBranchResponse(findActiveBranch(projectId, branchId));
  }

  @Transactional(readOnly = true)
  public List<ProjectBranchResponse> listBranches(UUID requesterUserId, UUID projectId, Short status) {
    requirePermission(projectId, requesterUserId, Permission.BRANCH_READ);
    if (status != null) {
      validateBranchStatus(status);
    }
    List<ProjectBranchEntity> branches = status == null
        ? projectBranchRepository.findByProjectIdAndDeletedAtIsNullOrderByUpdatedAtDesc(projectId)
        : projectBranchRepository.findByProjectIdAndStatusAndDeletedAtIsNullOrderByUpdatedAtDesc(projectId, status);
    return branches.stream().map(this::toProjectBranchResponse).toList();
  }

  @Transactional
  public ProjectBranchResponse updateBranch(
      UUID requesterUserId,
      UUID projectId,
      UUID branchId,
      UpdateProjectBranchRequest request) {
    requirePermission(projectId, requesterUserId, Permission.BRANCH_UPDATE);
    ProjectBranchEntity branch = findActiveBranch(projectId, branchId);

    if (request.name() != null) {
      String branchName = normalizeBranchName(request.name());
      ProjectBranchEntity existing = projectBranchRepository.findByProjectIdAndNameAndDeletedAtIsNull(
              projectId,
              branchName)
          .orElse(null);
      if (existing != null && !existing.getId().equals(branch.getId())) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "branch name already exists");
      }
      branch.setName(branchName);
    }

    if (request.status() != null) {
      validateBranchStatus(request.status());
      if (branch.getBranchType() == BRANCH_TYPE_MAIN && request.status() != BRANCH_STATUS_ACTIVE) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "main branch status cannot be changed");
      }
      branch.setStatus(request.status());
    }

    return toProjectBranchResponse(projectBranchRepository.save(branch));
  }

  @Transactional
  public void deleteBranch(UUID requesterUserId, UUID projectId, UUID branchId) {
    requirePermission(projectId, requesterUserId, Permission.BRANCH_DELETE);
    ProjectBranchEntity branch = findActiveBranch(projectId, branchId);
    if (branch.getBranchType() == BRANCH_TYPE_MAIN) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "main branch cannot be deleted");
    }

    BranchProtectionRuleEntity protection = findBestMatchingRule(projectId, branch.getName());
    if (protection != null && !protection.isAllowDeleteBranch()) {
      throw forbidden("branch protection does not allow deleting this branch");
    }

    branch.setDeletedAt(Instant.now());
    branch.setStatus(BRANCH_STATUS_ARCHIVED);
    projectBranchRepository.save(branch);
  }

  @Transactional
  public BranchProtectionRuleResponse createBranchProtectionRule(
      UUID requesterUserId,
      UUID projectId,
      CreateBranchProtectionRuleRequest request) {
    requirePermission(projectId, requesterUserId, Permission.RULE_WRITE);

    String branchPattern = normalizeBranchPattern(request.branchPattern());
    if (branchProtectionRuleRepository.existsByProjectIdAndBranchPatternAndDeletedAtIsNull(projectId, branchPattern)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "branch protection pattern already exists");
    }

    short minPushRole = request.minPushRole() == null ? DEFAULT_MIN_PUSH_ROLE : request.minPushRole();
    short minMergeRole = request.minMergeRole() == null ? DEFAULT_MIN_MERGE_ROLE : request.minMergeRole();
    validateMemberRole(minPushRole);
    validateMemberRole(minMergeRole);

    BranchProtectionRuleEntity rule = new BranchProtectionRuleEntity();
    rule.setProjectId(projectId);
    rule.setBranchPattern(branchPattern);
    rule.setMinPushRole(minPushRole);
    rule.setMinMergeRole(minMergeRole);
    rule.setRequirePr(Boolean.TRUE.equals(request.requirePr()));
    rule.setAllowForcePush(Boolean.TRUE.equals(request.allowForcePush()));
    rule.setAllowDeleteBranch(Boolean.TRUE.equals(request.allowDeleteBranch()));
    rule.setCreatedBy(requesterUserId);

    return toBranchProtectionRuleResponse(branchProtectionRuleRepository.save(rule));
  }

  @Transactional(readOnly = true)
  public BranchProtectionRuleResponse getBranchProtectionRule(
      UUID requesterUserId,
      UUID projectId,
      UUID ruleId) {
    requirePermission(projectId, requesterUserId, Permission.RULE_READ);
    return toBranchProtectionRuleResponse(findActiveRule(projectId, ruleId));
  }

  @Transactional(readOnly = true)
  public List<BranchProtectionRuleResponse> listBranchProtectionRules(UUID requesterUserId, UUID projectId) {
    requirePermission(projectId, requesterUserId, Permission.RULE_READ);
    return branchProtectionRuleRepository.findByProjectIdAndDeletedAtIsNullOrderByUpdatedAtDesc(projectId)
        .stream()
        .map(this::toBranchProtectionRuleResponse)
        .toList();
  }

  @Transactional
  public BranchProtectionRuleResponse updateBranchProtectionRule(
      UUID requesterUserId,
      UUID projectId,
      UUID ruleId,
      UpdateBranchProtectionRuleRequest request) {
    requirePermission(projectId, requesterUserId, Permission.RULE_WRITE);
    BranchProtectionRuleEntity rule = findActiveRule(projectId, ruleId);

    if (request.branchPattern() != null) {
      String branchPattern = normalizeBranchPattern(request.branchPattern());
      BranchProtectionRuleEntity existing = branchProtectionRuleRepository.findByProjectIdAndBranchPatternAndDeletedAtIsNull(
              projectId,
              branchPattern)
          .orElse(null);
      if (existing != null && !existing.getId().equals(rule.getId())) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "branch protection pattern already exists");
      }
      rule.setBranchPattern(branchPattern);
    }
    if (request.minPushRole() != null) {
      validateMemberRole(request.minPushRole());
      rule.setMinPushRole(request.minPushRole());
    }
    if (request.minMergeRole() != null) {
      validateMemberRole(request.minMergeRole());
      rule.setMinMergeRole(request.minMergeRole());
    }
    if (request.requirePr() != null) {
      rule.setRequirePr(request.requirePr());
    }
    if (request.allowForcePush() != null) {
      rule.setAllowForcePush(request.allowForcePush());
    }
    if (request.allowDeleteBranch() != null) {
      rule.setAllowDeleteBranch(request.allowDeleteBranch());
    }

    return toBranchProtectionRuleResponse(branchProtectionRuleRepository.save(rule));
  }

  @Transactional
  public void deleteBranchProtectionRule(UUID requesterUserId, UUID projectId, UUID ruleId) {
    requirePermission(projectId, requesterUserId, Permission.RULE_WRITE);
    BranchProtectionRuleEntity rule = findActiveRule(projectId, ruleId);
    rule.setDeletedAt(Instant.now());
    branchProtectionRuleRepository.save(rule);
  }

  @Transactional
  public BranchCheckpointResponse createCheckpoint(
      UUID requesterUserId,
      UUID projectId,
      UUID branchId,
      CreateBranchCheckpointRequest request) {
    requirePermission(projectId, requesterUserId, Permission.CHECKPOINT_CREATE);
    ProjectBranchEntity branch = findActiveBranch(projectId, branchId);
    if (branch.getStatus() != BRANCH_STATUS_ACTIVE) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "checkpoint can only be created on active branch");
    }

    BranchCheckpointEntity checkpoint = new BranchCheckpointEntity();
    checkpoint.setProjectId(projectId);
    checkpoint.setBranchId(branchId);
    checkpoint.setTitle(normalizeName(request.title(), 200, "checkpoint title"));
    checkpoint.setDescription(normalizeDescription(request.description()));
    checkpoint.setSnapshotRef(normalizeSnapshotRef(request.snapshotRef()));
    checkpoint.setSnapshotSizeBytes(request.snapshotSizeBytes());
    checkpoint.setFileCount(request.fileCount());
    checkpoint.setCreatedBy(requesterUserId);
    BranchCheckpointEntity savedCheckpoint = branchCheckpointRepository.save(checkpoint);

    branch.setHeadCheckpointId(savedCheckpoint.getId());
    projectBranchRepository.save(branch);

    return toBranchCheckpointResponse(savedCheckpoint);
  }

  @Transactional(readOnly = true)
  public BranchCheckpointResponse getCheckpoint(UUID requesterUserId, UUID projectId, UUID checkpointId) {
    requirePermission(projectId, requesterUserId, Permission.CHECKPOINT_READ);
    return toBranchCheckpointResponse(findActiveCheckpoint(projectId, checkpointId));
  }

  @Transactional(readOnly = true)
  public List<BranchCheckpointResponse> listBranchCheckpoints(UUID requesterUserId, UUID projectId, UUID branchId) {
    requirePermission(projectId, requesterUserId, Permission.CHECKPOINT_READ);
    findActiveBranch(projectId, branchId);
    return branchCheckpointRepository.findByProjectIdAndBranchIdAndDeletedAtIsNullOrderByCreatedAtDesc(projectId, branchId)
        .stream()
        .map(this::toBranchCheckpointResponse)
        .toList();
  }

  @Transactional
  public PublishRecordResponse createPublishRecord(
      UUID requesterUserId,
      UUID projectId,
      CreatePublishRecordRequest request) {
    return createPublishRecord(requesterUserId, projectId, request, null);
  }

  @Transactional
  public PublishRecordResponse createPublishRecord(
      UUID requesterUserId,
      UUID projectId,
      CreatePublishRecordRequest request,
      String idempotencyKey) {
    return projectIdempotencyService.execute(
        projectId,
        requesterUserId,
        IDEMPOTENCY_SCOPE_PUBLISH_CREATE,
        idempotencyKey,
        request,
        PublishRecordResponse.class,
        () -> createPublishRecordInternal(requesterUserId, projectId, request));
  }

  private PublishRecordResponse createPublishRecordInternal(
      UUID requesterUserId,
      UUID projectId,
      CreatePublishRecordRequest request) {
    ProjectMemberEntity operator = requirePermission(projectId, requesterUserId, Permission.PUBLISH_EXECUTE);

    ProjectBranchEntity sourceBranch = findActiveBranch(projectId, request.sourceBranchId());
    ProjectBranchEntity targetBranch = findActiveBranch(projectId, request.targetBranchId());
    if (sourceBranch.getStatus() != BRANCH_STATUS_ACTIVE || targetBranch.getStatus() != BRANCH_STATUS_ACTIVE) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "publish requires active branches");
    }
    if (sourceBranch.getId().equals(targetBranch.getId())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "source and target branch must be different");
    }

    BranchCheckpointEntity sourceCheckpoint = findActiveCheckpoint(projectId, request.sourceCheckpointId());
    if (!sourceCheckpoint.getBranchId().equals(sourceBranch.getId())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "source checkpoint does not belong to source branch");
    }

    BranchProtectionRuleEntity rule = findBestMatchingRule(projectId, targetBranch.getName());
    if (rule != null && !roleMeetsRequirement(operator.getRole(), rule.getMinMergeRole())) {
      throw forbidden("insufficient merge role for target branch protection");
    }

    short publishStatus = request.publishStatus() == null ? PUBLISH_STATUS_PENDING : request.publishStatus();
    validatePublishStatus(publishStatus);

    PublishRecordEntity record = new PublishRecordEntity();
    record.setProjectId(projectId);
    record.setSourceBranchId(sourceBranch.getId());
    record.setTargetBranchId(targetBranch.getId());
    record.setSourceCheckpointId(sourceCheckpoint.getId());
    record.setPublishStatus(publishStatus);
    record.setConflictSummary(normalizeDescription(request.conflictSummary()));
    record.setPublishedBy(requesterUserId);
    record.setPublishedAt(Instant.now());
    PublishRecordEntity saved = publishRecordRepository.save(record);

    if (publishStatus == PUBLISH_STATUS_SUCCESS) {
      targetBranch.setHeadCheckpointId(sourceCheckpoint.getId());
      projectBranchRepository.save(targetBranch);
    }

    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("sourceBranchId", sourceBranch.getId().toString());
    detail.put("targetBranchId", targetBranch.getId().toString());
    detail.put("sourceCheckpointId", sourceCheckpoint.getId().toString());
    detail.put("publishStatus", saved.getPublishStatus());
    detail.put("targetHeadCheckpointUpdated", publishStatus == PUBLISH_STATUS_SUCCESS);
    projectAuditLogService.record(
        projectId,
        requesterUserId,
        AUDIT_ACTION_PUBLISH_CREATED,
        "publish_record",
        saved.getId(),
        detail);

    return toPublishRecordResponse(saved);
  }

  @Transactional(readOnly = true)
  public PublishRecordResponse getPublishRecord(UUID requesterUserId, UUID projectId, UUID publishRecordId) {
    requirePermission(projectId, requesterUserId, Permission.PUBLISH_READ);
    return toPublishRecordResponse(findPublishRecord(projectId, publishRecordId));
  }

  @Transactional(readOnly = true)
  public List<PublishRecordResponse> listPublishRecords(
      UUID requesterUserId,
      UUID projectId,
      Short publishStatus,
      UUID targetBranchId) {
    requirePermission(projectId, requesterUserId, Permission.PUBLISH_READ);
    if (publishStatus != null) {
      validatePublishStatus(publishStatus);
    }
    if (targetBranchId != null) {
      findActiveBranch(projectId, targetBranchId);
    }

    List<PublishRecordEntity> records;
    if (publishStatus == null && targetBranchId == null) {
      records = publishRecordRepository.findByProjectIdOrderByPublishedAtDesc(projectId);
    } else if (publishStatus != null && targetBranchId == null) {
      records = publishRecordRepository.findByProjectIdAndPublishStatusOrderByCreatedAtDesc(projectId, publishStatus);
    } else if (publishStatus == null) {
      records = publishRecordRepository.findByProjectIdAndTargetBranchIdOrderByPublishedAtDesc(projectId, targetBranchId);
    } else {
      records = publishRecordRepository.findByProjectIdAndTargetBranchIdAndPublishStatusOrderByPublishedAtDesc(
          projectId,
          targetBranchId,
          publishStatus);
    }

    return records.stream().map(this::toPublishRecordResponse).toList();
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
      case MEMBER_ROLE_ADMIN -> true;
      case MEMBER_ROLE_MEMBER -> permission == Permission.BRANCH_READ
          || permission == Permission.BRANCH_CREATE
          || permission == Permission.RULE_READ
          || permission == Permission.CHECKPOINT_READ
          || permission == Permission.CHECKPOINT_CREATE
          || permission == Permission.PUBLISH_READ;
      case MEMBER_ROLE_VIEWER -> permission == Permission.BRANCH_READ
          || permission == Permission.RULE_READ
          || permission == Permission.CHECKPOINT_READ
          || permission == Permission.PUBLISH_READ;
      default -> false;
    };
  }

  private ProjectEntity findActiveProject(UUID projectId) {
    return projectRepository.findByIdAndDeletedAtIsNull(projectId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "project not found"));
  }

  private ProjectBranchEntity findActiveBranch(UUID projectId, UUID branchId) {
    return projectBranchRepository.findByIdAndProjectIdAndDeletedAtIsNull(branchId, projectId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "branch not found"));
  }

  private BranchProtectionRuleEntity findActiveRule(UUID projectId, UUID ruleId) {
    return branchProtectionRuleRepository.findByIdAndProjectIdAndDeletedAtIsNull(ruleId, projectId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "branch protection rule not found"));
  }

  private BranchCheckpointEntity findActiveCheckpoint(UUID projectId, UUID checkpointId) {
    return branchCheckpointRepository.findByIdAndProjectIdAndDeletedAtIsNull(checkpointId, projectId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "checkpoint not found"));
  }

  private PublishRecordEntity findPublishRecord(UUID projectId, UUID publishRecordId) {
    return publishRecordRepository.findByIdAndProjectId(publishRecordId, projectId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "publish record not found"));
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

  private String normalizeBranchName(String branchName) {
    String normalized = normalizeName(branchName, 128, "branch name");
    if (normalized.startsWith("/") || normalized.endsWith("/") || normalized.contains("//")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid branch name");
    }
    return normalized;
  }

  private String normalizeBranchPattern(String branchPattern) {
    String normalized = normalizeName(branchPattern, 128, "branch pattern");
    if (normalized.startsWith("/") || normalized.endsWith("/") || normalized.contains("//")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid branch pattern");
    }
    return normalized;
  }

  private String normalizeSnapshotRef(String snapshotRef) {
    return normalizeName(snapshotRef, 512, "snapshot ref");
  }

  private String normalizeDescription(String description) {
    if (description == null) {
      return null;
    }
    String normalized = description.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  private short inferDefaultBranchType(String branchName) {
    return "main".equals(branchName.toLowerCase(Locale.ROOT)) ? BRANCH_TYPE_MAIN : BRANCH_TYPE_FEATURE;
  }

  private void validateMemberRole(short role) {
    if (role < MEMBER_ROLE_OWNER || role > MEMBER_ROLE_VIEWER) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid member role");
    }
  }

  private void validateBranchType(short branchType) {
    if (branchType != BRANCH_TYPE_MAIN
        && branchType != BRANCH_TYPE_FEATURE
        && branchType != BRANCH_TYPE_BUGFIX
        && branchType != BRANCH_TYPE_RELEASE
        && branchType != BRANCH_TYPE_HOTFIX) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid branch type");
    }
  }

  private void validateBranchStatus(short status) {
    if (status != BRANCH_STATUS_ACTIVE && status != BRANCH_STATUS_MERGED && status != BRANCH_STATUS_ARCHIVED) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid branch status");
    }
  }

  private void validatePublishStatus(short status) {
    if (status != PUBLISH_STATUS_PENDING && status != PUBLISH_STATUS_SUCCESS && status != PUBLISH_STATUS_FAILED) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid publish status");
    }
  }

  private boolean roleMeetsRequirement(short role, short requiredRole) {
    validateMemberRole(role);
    validateMemberRole(requiredRole);
    return role <= requiredRole;
  }

  private BranchProtectionRuleEntity findBestMatchingRule(UUID projectId, String branchName) {
    return branchProtectionRuleRepository.findByProjectIdAndDeletedAtIsNullOrderByUpdatedAtDesc(projectId)
        .stream()
        .filter(rule -> branchPatternMatches(rule.getBranchPattern(), branchName))
        .max(Comparator.comparingInt(rule -> branchPatternSpecificity(rule.getBranchPattern())))
        .orElse(null);
  }

  private boolean branchPatternMatches(String branchPattern, String branchName) {
    return branchName.matches(wildcardToRegex(branchPattern));
  }

  private String wildcardToRegex(String wildcardPattern) {
    StringBuilder regex = new StringBuilder("^");
    for (int i = 0; i < wildcardPattern.length(); i++) {
      char ch = wildcardPattern.charAt(i);
      if (ch == '*') {
        regex.append(".*");
        continue;
      }
      if ("\\.[]{}()+-^$|?".indexOf(ch) >= 0) {
        regex.append("\\");
      }
      regex.append(ch);
    }
    regex.append("$");
    return regex.toString();
  }

  private int branchPatternSpecificity(String branchPattern) {
    int score = 0;
    for (int i = 0; i < branchPattern.length(); i++) {
      if (branchPattern.charAt(i) != '*') {
        score++;
      }
    }
    return score;
  }

  private ResponseStatusException forbidden(String message) {
    return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
  }

  private ProjectBranchResponse toProjectBranchResponse(ProjectBranchEntity entity) {
    return new ProjectBranchResponse(
        entity.getId(),
        entity.getProjectId(),
        entity.getName(),
        entity.getBranchType(),
        entity.getBasedOnBranchId(),
        entity.getHeadCheckpointId(),
        entity.getStatus(),
        entity.getCreatedBy(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }

  private BranchProtectionRuleResponse toBranchProtectionRuleResponse(BranchProtectionRuleEntity entity) {
    return new BranchProtectionRuleResponse(
        entity.getId(),
        entity.getProjectId(),
        entity.getBranchPattern(),
        entity.getMinPushRole(),
        entity.getMinMergeRole(),
        entity.isRequirePr(),
        entity.isAllowForcePush(),
        entity.isAllowDeleteBranch(),
        entity.getCreatedBy(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }

  private BranchCheckpointResponse toBranchCheckpointResponse(BranchCheckpointEntity entity) {
    return new BranchCheckpointResponse(
        entity.getId(),
        entity.getProjectId(),
        entity.getBranchId(),
        entity.getTitle(),
        entity.getDescription(),
        entity.getSnapshotRef(),
        entity.getSnapshotSizeBytes(),
        entity.getFileCount(),
        entity.getCreatedBy(),
        entity.getCreatedAt());
  }

  private PublishRecordResponse toPublishRecordResponse(PublishRecordEntity entity) {
    return new PublishRecordResponse(
        entity.getId(),
        entity.getProjectId(),
        entity.getSourceBranchId(),
        entity.getTargetBranchId(),
        entity.getSourceCheckpointId(),
        entity.getPublishStatus(),
        entity.getConflictSummary(),
        entity.getPublishedBy(),
        entity.getPublishedAt(),
        entity.getCreatedAt());
  }
}
