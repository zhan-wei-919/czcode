package com.czcode.authservice.common.web;

import com.czcode.authservice.common.api.ApiErrorResponse;
import com.czcode.authservice.common.error.AuthErrorCodes;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class RestSecurityExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

  private final ObjectMapper objectMapper = JsonMapper.builder()
      .addModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
      .build();

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException) throws IOException {
    write(
        response,
        request,
        HttpStatus.UNAUTHORIZED,
        AuthErrorCodes.UNAUTHORIZED,
        "unauthorized");
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException) throws IOException {
    write(
        response,
        request,
        HttpStatus.FORBIDDEN,
        AuthErrorCodes.FORBIDDEN,
        "forbidden");
  }

  private void write(
      HttpServletResponse response,
      HttpServletRequest request,
      HttpStatus status,
      String code,
      String message) throws IOException {
    ApiErrorResponse body = new ApiErrorResponse(
        code,
        message,
        resolveRequestId(request),
        request.getRequestURI(),
        Instant.now(),
        List.of());

    response.setStatus(status.value());
    response.setCharacterEncoding("UTF-8");
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write(objectMapper.writeValueAsString(body));
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
}
