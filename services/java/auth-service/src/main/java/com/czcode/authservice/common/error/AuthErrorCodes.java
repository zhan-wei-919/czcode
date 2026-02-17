package com.czcode.authservice.common.error;

public final class AuthErrorCodes {

  private AuthErrorCodes() {
  }

  public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
  public static final String REQUEST_BODY_INVALID = "REQUEST_BODY_INVALID";
  public static final String BAD_REQUEST = "BAD_REQUEST";
  public static final String UNAUTHORIZED = "UNAUTHORIZED";
  public static final String FORBIDDEN = "FORBIDDEN";
  public static final String NOT_FOUND = "NOT_FOUND";
  public static final String CONFLICT = "CONFLICT";
  public static final String LOCKED = "LOCKED";
  public static final String RATE_LIMITED = "RATE_LIMITED";
  public static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
  public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

  public static final String AUTH_CODE_RATE_LIMITED = "AUTH_CODE_RATE_LIMITED";
  public static final String AUTH_EMAIL_ALREADY_REGISTERED = "AUTH_EMAIL_ALREADY_REGISTERED";
  public static final String AUTH_EMAIL_CODE_NOT_FOUND = "AUTH_EMAIL_CODE_NOT_FOUND";
  public static final String AUTH_EMAIL_CODE_EXPIRED = "AUTH_EMAIL_CODE_EXPIRED";
  public static final String AUTH_EMAIL_CODE_INVALID = "AUTH_EMAIL_CODE_INVALID";
  public static final String AUTH_INVALID_CREDENTIALS = "AUTH_INVALID_CREDENTIALS";
  public static final String AUTH_REFRESH_TOKEN_INVALID = "AUTH_REFRESH_TOKEN_INVALID";
  public static final String AUTH_REFRESH_TOKEN_EXPIRED = "AUTH_REFRESH_TOKEN_EXPIRED";
  public static final String AUTH_ACCOUNT_NOT_FOUND = "AUTH_ACCOUNT_NOT_FOUND";
  public static final String AUTH_ACCOUNT_DISABLED = "AUTH_ACCOUNT_DISABLED";
  public static final String AUTH_ACCOUNT_LOCKED = "AUTH_ACCOUNT_LOCKED";
  public static final String AUTH_UPSTREAM_USER_SERVICE_FAILED = "AUTH_UPSTREAM_USER_SERVICE_FAILED";
  public static final String AUTH_UPSTREAM_USER_SERVICE_UNAVAILABLE = "AUTH_UPSTREAM_USER_SERVICE_UNAVAILABLE";
}
