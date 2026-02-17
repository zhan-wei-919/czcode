package com.czcode.userservice.common.api;

public record ApiErrorDetail(
    String field,
    String message
) {
}
