package com.czcode.projectservice.common.api;

public record ApiErrorDetail(
    String field,
    String message
) {
}
