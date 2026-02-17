package com.czcode.userservice.common.error;

public final class UserErrorCodes {

  private UserErrorCodes() {
  }

  public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
  public static final String REQUEST_BODY_INVALID = "REQUEST_BODY_INVALID";
  public static final String BAD_REQUEST = "BAD_REQUEST";
  public static final String NOT_FOUND = "NOT_FOUND";
  public static final String RATE_LIMITED = "RATE_LIMITED";
  public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

  public static final String USER_NOT_FOUND = "USER_NOT_FOUND";
  public static final String USER_STATUS_INVALID = "USER_STATUS_INVALID";
}
