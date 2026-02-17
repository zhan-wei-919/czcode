package com.czcode.projectservice.common.web;

import com.czcode.projectservice.common.api.ApiErrorResponse;
import com.czcode.projectservice.common.error.ProjectErrorCodes;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ApiRateLimitFilter extends OncePerRequestFilter {

  private final RateLimitProperties rateLimitProperties;
  private final MeterRegistry meterRegistry;
  private final String serviceName;
  private final AntPathMatcher pathMatcher = new AntPathMatcher();
  private final ConcurrentMap<String, Bucket> bucketBySubjectAndRule = new ConcurrentHashMap<>();
  private final ObjectMapper objectMapper = JsonMapper.builder()
      .addModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
      .build();

  public ApiRateLimitFilter(
      RateLimitProperties rateLimitProperties,
      MeterRegistry meterRegistry,
      @Value("${spring.application.name:project-service}") String serviceName) {
    this.rateLimitProperties = rateLimitProperties;
    this.meterRegistry = meterRegistry;
    this.serviceName = serviceName;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    RateLimitProperties.Rule rule = resolveRule(request);
    if (rule == null) {
      filterChain.doFilter(request, response);
      return;
    }

    maybeTrimBuckets();

    String subject = resolveSubject(request);
    String key = rule.getId() + ":" + subject;
    Bucket bucket = bucketBySubjectAndRule.computeIfAbsent(key, ignored -> createBucket(rule));
    if (bucket.tryConsume(1)) {
      filterChain.doFilter(request, response);
      return;
    }

    Counter.builder("czcode.api.ratelimit.blocked.total")
        .tag("service", serviceName)
        .tag("rule", rule.getId())
        .register(meterRegistry)
        .increment();

    ApiErrorResponse body = new ApiErrorResponse(
        ProjectErrorCodes.RATE_LIMITED,
        "rate limit exceeded",
        resolveRequestId(request),
        request.getRequestURI(),
        Instant.now(),
        List.of());

    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    response.setCharacterEncoding("UTF-8");
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setHeader("Retry-After", Long.toString(Math.max(1L, rule.getRefillDurationSeconds())));
    response.getWriter().write(objectMapper.writeValueAsString(body));
  }

  private RateLimitProperties.Rule resolveRule(HttpServletRequest request) {
    if (!rateLimitProperties.isEnabled() || rateLimitProperties.getRules().isEmpty()) {
      return null;
    }
    String path = request.getRequestURI();
    String method = request.getMethod();
    for (RateLimitProperties.Rule rule : rateLimitProperties.getRules()) {
      if (!isRuleValid(rule)) {
        continue;
      }
      boolean methodMatch = "*".equals(rule.getMethod())
          || HttpMethod.valueOf(method).name().equalsIgnoreCase(rule.getMethod());
      if (methodMatch && pathMatcher.match(rule.getPath(), path)) {
        return rule;
      }
    }
    return null;
  }

  private boolean isRuleValid(RateLimitProperties.Rule rule) {
    return rule != null
        && rule.getId() != null
        && !rule.getId().isBlank()
        && rule.getPath() != null
        && !rule.getPath().isBlank()
        && rule.getCapacity() > 0
        && rule.getRefillTokens() > 0
        && rule.getRefillDurationSeconds() > 0;
  }

  private Bucket createBucket(RateLimitProperties.Rule rule) {
    Bandwidth bandwidth = Bandwidth.classic(
        rule.getCapacity(),
        Refill.intervally(rule.getRefillTokens(), Duration.ofSeconds(rule.getRefillDurationSeconds())));
    return Bucket.builder().addLimit(bandwidth).build();
  }

  private void maybeTrimBuckets() {
    int maxSubjects = Math.max(rateLimitProperties.getMaxSubjects(), 1000);
    if (bucketBySubjectAndRule.size() > maxSubjects) {
      bucketBySubjectAndRule.clear();
    }
  }

  private String resolveSubject(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      return forwardedFor.split(",")[0].trim();
    }
    String remoteAddr = request.getRemoteAddr();
    if (remoteAddr != null && !remoteAddr.isBlank()) {
      return remoteAddr;
    }
    return "unknown-" + UUID.randomUUID();
  }

  private String resolveRequestId(HttpServletRequest request) {
    Object requestId = request.getAttribute(RequestTraceFilter.REQUEST_ID_ATTR);
    if (requestId instanceof String value && !value.isBlank()) {
      return value;
    }
    String headerValue = request.getHeader(RequestTraceFilter.REQUEST_ID_HEADER);
    if (headerValue != null && !headerValue.isBlank()) {
      return headerValue;
    }
    return UUID.randomUUID().toString();
  }
}
