package com.czcode.projectservice.project.service;

import com.czcode.projectservice.project.entity.ProjectAuditLogEntity;
import com.czcode.projectservice.project.repository.ProjectAuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ProjectAuditLogService {

  private final ProjectAuditLogRepository projectAuditLogRepository;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public ProjectAuditLogService(ProjectAuditLogRepository projectAuditLogRepository) {
    this.projectAuditLogRepository = projectAuditLogRepository;
  }

  public void record(
      UUID projectId,
      UUID actorUserId,
      String action,
      String targetType,
      UUID targetId,
      Map<String, Object> detail) {
    ProjectAuditLogEntity log = new ProjectAuditLogEntity();
    log.setProjectId(projectId);
    log.setActorUserId(actorUserId);
    log.setAction(action);
    log.setTargetType(targetType);
    log.setTargetId(targetId);
    log.setDetailJson(writeDetailJson(detail));
    projectAuditLogRepository.save(log);
  }

  private String writeDetailJson(Map<String, Object> detail) {
    if (detail == null || detail.isEmpty()) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(detail);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("failed to serialize audit detail", ex);
    }
  }
}
