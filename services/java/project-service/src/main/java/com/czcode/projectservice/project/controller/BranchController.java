package com.czcode.projectservice.project.controller;

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
import com.czcode.projectservice.project.service.BranchPublishService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/projects/{projectId}")
public class BranchController {

  private final BranchPublishService branchPublishService;

  public BranchController(BranchPublishService branchPublishService) {
    this.branchPublishService = branchPublishService;
  }

  @PostMapping("/branches")
  public ResponseEntity<ProjectBranchResponse> createBranch(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable UUID projectId,
      @Valid @RequestBody CreateProjectBranchRequest request) {
    ProjectBranchResponse created = branchPublishService.createBranch(
        requireCurrentUserId(principal),
        projectId,
        request);
    return ResponseEntity
        .created(URI.create("/api/v1/projects/" + projectId + "/branches/" + created.id()))
        .body(created);
  }

  @GetMapping("/branches/{branchId}")
  public ProjectBranchResponse getBranch(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable UUID projectId,
      @PathVariable UUID branchId) {
    return branchPublishService.getBranch(requireCurrentUserId(principal), projectId, branchId);
  }

  @GetMapping("/branches")
  public List<ProjectBranchResponse> listBranches(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable UUID projectId,
      @RequestParam(required = false) Short status) {
    return branchPublishService.listBranches(requireCurrentUserId(principal), projectId, status);
  }

  @PatchMapping("/branches/{branchId}")
  public ProjectBranchResponse updateBranch(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable UUID projectId,
      @PathVariable UUID branchId,
      @Valid @RequestBody UpdateProjectBranchRequest request) {
    return branchPublishService.updateBranch(requireCurrentUserId(principal), projectId, branchId, request);
  }

  @DeleteMapping("/branches/{branchId}")
  public ResponseEntity<Void> deleteBranch(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable UUID projectId,
      @PathVariable UUID branchId) {
    branchPublishService.deleteBranch(requireCurrentUserId(principal), projectId, branchId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/branch-protection-rules")
  public ResponseEntity<BranchProtectionRuleResponse> createBranchProtectionRule(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable UUID projectId,
      @Valid @RequestBody CreateBranchProtectionRuleRequest request) {
    BranchProtectionRuleResponse created = branchPublishService.createBranchProtectionRule(
        requireCurrentUserId(principal),
        projectId,
        request);
    return ResponseEntity
        .created(URI.create("/api/v1/projects/" + projectId + "/branch-protection-rules/" + created.id()))
        .body(created);
  }

  @GetMapping("/branch-protection-rules/{ruleId}")
  public BranchProtectionRuleResponse getBranchProtectionRule(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable UUID projectId,
      @PathVariable UUID ruleId) {
    return branchPublishService.getBranchProtectionRule(requireCurrentUserId(principal), projectId, ruleId);
  }

  @GetMapping("/branch-protection-rules")
  public List<BranchProtectionRuleResponse> listBranchProtectionRules(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable UUID projectId) {
    return branchPublishService.listBranchProtectionRules(requireCurrentUserId(principal), projectId);
  }

  @PatchMapping("/branch-protection-rules/{ruleId}")
  public BranchProtectionRuleResponse updateBranchProtectionRule(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable UUID projectId,
      @PathVariable UUID ruleId,
      @Valid @RequestBody UpdateBranchProtectionRuleRequest request) {
    return branchPublishService.updateBranchProtectionRule(requireCurrentUserId(principal), projectId, ruleId, request);
  }

  @DeleteMapping("/branch-protection-rules/{ruleId}")
  public ResponseEntity<Void> deleteBranchProtectionRule(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable UUID projectId,
      @PathVariable UUID ruleId) {
    branchPublishService.deleteBranchProtectionRule(requireCurrentUserId(principal), projectId, ruleId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/branches/{branchId}/checkpoints")
  public ResponseEntity<BranchCheckpointResponse> createCheckpoint(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable UUID projectId,
      @PathVariable UUID branchId,
      @Valid @RequestBody CreateBranchCheckpointRequest request) {
    BranchCheckpointResponse created = branchPublishService.createCheckpoint(
        requireCurrentUserId(principal),
        projectId,
        branchId,
        request);
    return ResponseEntity
        .created(URI.create("/api/v1/projects/" + projectId + "/branches/" + branchId + "/checkpoints/" + created.id()))
        .body(created);
  }

  @GetMapping("/branches/{branchId}/checkpoints")
  public List<BranchCheckpointResponse> listBranchCheckpoints(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable UUID projectId,
      @PathVariable UUID branchId) {
    return branchPublishService.listBranchCheckpoints(requireCurrentUserId(principal), projectId, branchId);
  }

  @GetMapping("/checkpoints/{checkpointId}")
  public BranchCheckpointResponse getCheckpoint(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable UUID projectId,
      @PathVariable UUID checkpointId) {
    return branchPublishService.getCheckpoint(requireCurrentUserId(principal), projectId, checkpointId);
  }

  @PostMapping("/publish-records")
  public ResponseEntity<PublishRecordResponse> createPublishRecord(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable UUID projectId,
      @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody CreatePublishRecordRequest request) {
    PublishRecordResponse created = branchPublishService.createPublishRecord(
        requireCurrentUserId(principal),
        projectId,
        request,
        idempotencyKey);
    return ResponseEntity
        .created(URI.create("/api/v1/projects/" + projectId + "/publish-records/" + created.id()))
        .body(created);
  }

  @GetMapping("/publish-records/{publishRecordId}")
  public PublishRecordResponse getPublishRecord(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable UUID projectId,
      @PathVariable UUID publishRecordId) {
    return branchPublishService.getPublishRecord(requireCurrentUserId(principal), projectId, publishRecordId);
  }

  @GetMapping("/publish-records")
  public List<PublishRecordResponse> listPublishRecords(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable UUID projectId,
      @RequestParam(required = false) Short publishStatus,
      @RequestParam(required = false) UUID targetBranchId) {
    return branchPublishService.listPublishRecords(
        requireCurrentUserId(principal),
        projectId,
        publishStatus,
        targetBranchId);
  }

  private UUID requireCurrentUserId(AuthPrincipal principal) {
    if (principal == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized");
    }
    return principal.userId();
  }
}
