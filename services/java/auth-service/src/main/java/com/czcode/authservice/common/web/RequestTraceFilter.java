package com.czcode.authservice.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceFilter extends OncePerRequestFilter {

  public static final String REQUEST_ID_HEADER = "X-Request-Id";
  public static final String REQUEST_ID_ATTR = "requestId";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    String requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER));
    request.setAttribute(REQUEST_ID_ATTR, requestId);
    response.setHeader(REQUEST_ID_HEADER, requestId);

    MDC.put("requestId", requestId);
    MDC.put("traceId", requestId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove("requestId");
      MDC.remove("traceId");
    }
  }

  private String resolveRequestId(String requestIdHeader) {
    if (requestIdHeader == null) {
      return UUID.randomUUID().toString();
    }
    String normalized = requestIdHeader.trim();
    if (normalized.isEmpty() || normalized.length() > 128) {
      return UUID.randomUUID().toString();
    }
    return normalized;
  }
}
