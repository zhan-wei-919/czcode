package com.czcode.authservice.common.api;

public record ApiErrorDetail(
    String field,
    String message
) {
}
