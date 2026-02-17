package com.czcode.userservice.common.web;

import com.czcode.userservice.common.api.ApiErrorDetail;
import com.czcode.userservice.common.api.ApiErrorResponse;
import com.czcode.userservice.common.error.UserErrorCodes;
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
      Map.entry("user not found", UserErrorCodes.USER_NOT_FOUND),
      Map.entry("invalid status", UserErrorCodes.USER_STATUS_INVALID));

  private final MeterRegistry meterRegistry;
  private final String serviceName;

  public GlobalExceptionHandler(
      MeterRegistry meterRegistry,
      @Value("${spring.application.name:user-service}") String serviceName) {
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
        UserErrorCodes.VALIDATION_FAILED,
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
        UserErrorCodes.VALIDATION_FAILED,
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
        UserErrorCodes.VALIDATION_FAILED,
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
        UserErrorCodes.REQUEST_BODY_INVALID,
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
        UserErrorCodes.INTERNAL_ERROR,
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
    }
    return switch (status) {
      case BAD_REQUEST -> UserErrorCodes.BAD_REQUEST;
      case NOT_FOUND -> UserErrorCodes.NOT_FOUND;
      case TOO_MANY_REQUESTS -> UserErrorCodes.RATE_LIMITED;
      default -> status.is5xxServerError()
          ? UserErrorCodes.INTERNAL_ERROR
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
