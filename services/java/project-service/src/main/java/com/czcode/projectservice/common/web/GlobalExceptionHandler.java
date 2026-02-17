package com.czcode.projectservice.common.web;

import com.czcode.projectservice.common.api.ApiErrorDetail;
import com.czcode.projectservice.common.api.ApiErrorResponse;
import com.czcode.projectservice.common.error.ProjectErrorCodes;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Map<String, String> DOMAIN_REASON_TO_CODE = Map.ofEntries(
      Map.entry("owner user id must be current user", ProjectErrorCodes.PROJECT_OWNER_MISMATCH),
      Map.entry("project key already exists", ProjectErrorCodes.PROJECT_KEY_EXISTS),
      Map.entry("project not found", ProjectErrorCodes.PROJECT_NOT_FOUND),
      Map.entry("project access denied", ProjectErrorCodes.PROJECT_ACCESS_DENIED),
      Map.entry("insufficient project permission", ProjectErrorCodes.PROJECT_PERMISSION_DENIED),
      Map.entry("target member not found", ProjectErrorCodes.PROJECT_MEMBER_NOT_FOUND),
      Map.entry("target owner must be different from current owner", ProjectErrorCodes.PROJECT_OWNER_TRANSFER_INVALID_TARGET),
      Map.entry("target member is already owner", ProjectErrorCodes.PROJECT_OWNER_ALREADY_OWNER),
      Map.entry("pending invitation already exists for this email", ProjectErrorCodes.PROJECT_INVITATION_PENDING_EXISTS),
      Map.entry("invitation not found", ProjectErrorCodes.PROJECT_INVITATION_NOT_FOUND),
      Map.entry("invitation is not pending", ProjectErrorCodes.PROJECT_INVITATION_NOT_PENDING),
      Map.entry("invitation has expired", ProjectErrorCodes.PROJECT_INVITATION_EXPIRED),
      Map.entry("invitation email does not match current user", ProjectErrorCodes.PROJECT_INVITATION_EMAIL_MISMATCH),
      Map.entry("owner cannot accept invitation", ProjectErrorCodes.PROJECT_INVITATION_OWNER_ACCEPT_FORBIDDEN),
      Map.entry("member not found", ProjectErrorCodes.PROJECT_MEMBER_NOT_FOUND),
      Map.entry("owner member cannot be removed directly", ProjectErrorCodes.PROJECT_MEMBER_OWNER_REMOVE_FORBIDDEN),
      Map.entry("no permission to grant role", ProjectErrorCodes.PROJECT_MEMBER_ROLE_GRANT_FORBIDDEN),
      Map.entry("only owner can grant owner role", ProjectErrorCodes.PROJECT_MEMBER_ROLE_GRANT_FORBIDDEN),
      Map.entry("only owner can modify owner member", ProjectErrorCodes.PROJECT_MEMBER_ROLE_GRANT_FORBIDDEN),
      Map.entry("only owner can remove admin member", ProjectErrorCodes.PROJECT_MEMBER_ROLE_GRANT_FORBIDDEN),
      Map.entry("file node not found", ProjectErrorCodes.PROJECT_FILE_NODE_NOT_FOUND),
      Map.entry("file node path already exists", ProjectErrorCodes.PROJECT_FILE_PATH_EXISTS),
      Map.entry("target path already exists", ProjectErrorCodes.PROJECT_FILE_PATH_EXISTS),
      Map.entry("branch not found", ProjectErrorCodes.PROJECT_BRANCH_NOT_FOUND),
      Map.entry("branch name already exists", ProjectErrorCodes.PROJECT_BRANCH_NAME_EXISTS),
      Map.entry("branch protection pattern already exists", ProjectErrorCodes.PROJECT_BRANCH_PROTECTION_RULE_EXISTS),
      Map.entry("branch protection rule not found", ProjectErrorCodes.PROJECT_BRANCH_PROTECTION_RULE_NOT_FOUND),
      Map.entry("checkpoint not found", ProjectErrorCodes.PROJECT_CHECKPOINT_NOT_FOUND),
      Map.entry("publish record not found", ProjectErrorCodes.PROJECT_PUBLISH_RECORD_NOT_FOUND),
      Map.entry("main branch cannot be deleted", ProjectErrorCodes.PROJECT_BRANCH_PROTECTED),
      Map.entry("main branch status cannot be changed", ProjectErrorCodes.PROJECT_BRANCH_PROTECTED),
      Map.entry("branch protection does not allow deleting this branch", ProjectErrorCodes.PROJECT_BRANCH_PROTECTED),
      Map.entry("insufficient merge role for target branch protection", ProjectErrorCodes.PROJECT_BRANCH_PROTECTED),
      Map.entry("based-on branch is not active", ProjectErrorCodes.PROJECT_BRANCH_INVALID_STATE),
      Map.entry("publish requires active branches", ProjectErrorCodes.PROJECT_PUBLISH_INVALID_REQUEST),
      Map.entry("source and target branch must be different", ProjectErrorCodes.PROJECT_PUBLISH_INVALID_REQUEST),
      Map.entry("source checkpoint does not belong to source branch", ProjectErrorCodes.PROJECT_PUBLISH_INVALID_REQUEST),
      Map.entry("idempotency key reused with different request", ProjectErrorCodes.PROJECT_IDEMPOTENCY_CONFLICT),
      Map.entry("unauthorized", ProjectErrorCodes.UNAUTHORIZED));

  private final MeterRegistry meterRegistry;
  private final String serviceName;

  public GlobalExceptionHandler(
      MeterRegistry meterRegistry,
      @Value("${spring.application.name:project-service}") String serviceName) {
    this.meterRegistry = meterRegistry;
    this.serviceName = serviceName;
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public org.springframework.http.ResponseEntity<ApiErrorResponse> handleValidation(
      MethodArgumentNotValidException ex,
      HttpServletRequest request) {
    List<ApiErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
        .map(this::toDetail)
        .toList();
    return buildResponse(
        request,
        HttpStatus.BAD_REQUEST,
        ProjectErrorCodes.VALIDATION_FAILED,
        "request validation failed",
        details);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public org.springframework.http.ResponseEntity<ApiErrorResponse> handleConstraintViolation(
      ConstraintViolationException ex,
      HttpServletRequest request) {
    List<ApiErrorDetail> details = ex.getConstraintViolations().stream()
        .map(violation -> new ApiErrorDetail(
            violation.getPropertyPath().toString(),
            violation.getMessage()))
        .toList();
    return buildResponse(
        request,
        HttpStatus.BAD_REQUEST,
        ProjectErrorCodes.VALIDATION_FAILED,
        "request validation failed",
        details);
  }

  @ExceptionHandler(ServletRequestBindingException.class)
  public org.springframework.http.ResponseEntity<ApiErrorResponse> handleRequestBinding(
      ServletRequestBindingException ex,
      HttpServletRequest request) {
    String fieldName = ex instanceof MissingRequestHeaderException missingHeaderException
        ? missingHeaderException.getHeaderName()
        : "request";
    return buildResponse(
        request,
        HttpStatus.BAD_REQUEST,
        ProjectErrorCodes.VALIDATION_FAILED,
        "request validation failed",
        List.of(new ApiErrorDetail(fieldName, ex.getMessage())));
  }

  @ExceptionHandler(ResponseStatusException.class)
  public org.springframework.http.ResponseEntity<ApiErrorResponse> handleResponseStatus(
      ResponseStatusException ex,
      HttpServletRequest request) {
    HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
    String message = ex.getReason() == null || ex.getReason().isBlank()
        ? status.getReasonPhrase()
        : ex.getReason();
    return buildResponse(request, status, mapCode(status, ex.getReason()), message, List.of());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public org.springframework.http.ResponseEntity<ApiErrorResponse> handleMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpServletRequest request) {
    return buildResponse(
        request,
        HttpStatus.BAD_REQUEST,
        ProjectErrorCodes.REQUEST_BODY_INVALID,
        "request body is invalid",
        List.of());
  }

  @ExceptionHandler(Exception.class)
  public org.springframework.http.ResponseEntity<ApiErrorResponse> handleUnexpected(
      Exception ex,
      HttpServletRequest request) {
    return buildResponse(
        request,
        HttpStatus.INTERNAL_SERVER_ERROR,
        ProjectErrorCodes.INTERNAL_ERROR,
        "internal server error",
        List.of());
  }

  private org.springframework.http.ResponseEntity<ApiErrorResponse> buildResponse(
      HttpServletRequest request,
      HttpStatusCode status,
      String code,
      String message,
      List<ApiErrorDetail> details) {
    recordErrorMetric(status, code);
    ApiErrorResponse body = new ApiErrorResponse(
        code,
        message,
        resolveRequestId(request),
        request.getRequestURI(),
        Instant.now(),
        details);
    return org.springframework.http.ResponseEntity.status(status).body(body);
  }

  private String mapCode(HttpStatus status, String reason) {
    if (reason != null && !reason.isBlank()) {
      String domainCode = DOMAIN_REASON_TO_CODE.get(reason);
      if (domainCode != null) {
        return domainCode;
      }
      if (reason.startsWith("Idempotency-Key ")) {
        return ProjectErrorCodes.VALIDATION_FAILED;
      }
    }
    return switch (status) {
      case BAD_REQUEST -> ProjectErrorCodes.BAD_REQUEST;
      case UNAUTHORIZED -> ProjectErrorCodes.UNAUTHORIZED;
      case FORBIDDEN -> ProjectErrorCodes.FORBIDDEN;
      case NOT_FOUND -> ProjectErrorCodes.NOT_FOUND;
      case CONFLICT -> ProjectErrorCodes.CONFLICT;
      case LOCKED -> ProjectErrorCodes.LOCKED;
      case TOO_MANY_REQUESTS -> ProjectErrorCodes.RATE_LIMITED;
      default -> status.is5xxServerError()
          ? ProjectErrorCodes.INTERNAL_ERROR
          : "HTTP_" + status.value();
    };
  }

  private void recordErrorMetric(HttpStatusCode status, String code) {
    Counter.builder("czcode.api.errors.total")
        .tag("service", serviceName)
        .tag("code", code)
        .tag("status", Integer.toString(status.value()))
        .register(meterRegistry)
        .increment();
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
    return "unknown";
  }

  private ApiErrorDetail toDetail(FieldError fieldError) {
    return new ApiErrorDetail(
        fieldError.getField(),
        fieldError.getDefaultMessage() == null ? "invalid value" : fieldError.getDefaultMessage());
  }
}
