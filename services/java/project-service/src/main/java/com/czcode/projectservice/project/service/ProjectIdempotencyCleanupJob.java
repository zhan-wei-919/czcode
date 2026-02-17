package com.czcode.projectservice.project.service;

import com.czcode.projectservice.project.entity.ProjectIdempotencyRecordEntity;
import com.czcode.projectservice.project.repository.ProjectIdempotencyRecordRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(
    prefix = "project.idempotency.cleanup",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ProjectIdempotencyCleanupJob {

  private static final Logger log = LoggerFactory.getLogger(ProjectIdempotencyCleanupJob.class);

  private final ProjectIdempotencyRecordRepository repository;
  private final MeterRegistry meterRegistry;
  private final int batchSize;
  private final int maxBatchesPerRun;

  public ProjectIdempotencyCleanupJob(
      ProjectIdempotencyRecordRepository repository,
      MeterRegistry meterRegistry,
      @Value("${project.idempotency.cleanup.batch-size:500}") int batchSize,
      @Value("${project.idempotency.cleanup.max-batches-per-run:10}") int maxBatchesPerRun) {
    this.repository = repository;
    this.meterRegistry = meterRegistry;
    this.batchSize = Math.max(1, batchSize);
    this.maxBatchesPerRun = Math.max(1, maxBatchesPerRun);
  }

  @Scheduled(
      initialDelayString = "${project.idempotency.cleanup.initial-delay-ms:60000}",
      fixedDelayString = "${project.idempotency.cleanup.fixed-delay-ms:300000}")
  @Transactional
  public void cleanupExpiredRecords() {
    int totalDeleted = 0;
    for (int i = 0; i < maxBatchesPerRun; i++) {
      List<ProjectIdempotencyRecordEntity> expired = repository.findByExpiresAtBeforeOrderByExpiresAtAsc(
          Instant.now(),
          PageRequest.of(0, batchSize));
      if (expired.isEmpty()) {
        break;
      }
      repository.deleteAllInBatch(expired);
      totalDeleted += expired.size();
    }

    if (totalDeleted > 0) {
      Counter.builder("czcode.project.idempotency.cleanup.deleted.total")
          .register(meterRegistry)
          .increment(totalDeleted);
      log.info("cleaned up {} expired project idempotency records", totalDeleted);
    }
  }
}
