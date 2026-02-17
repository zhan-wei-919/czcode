package com.czcode.userservice.common.api;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
    String code,
    String message,
    String requestId,
    String path,
    Instant timestamp,
    List<ApiErrorDetail> details
) {
}
