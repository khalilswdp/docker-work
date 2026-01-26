package com.example.demo.exception.identity.sdk.authentication.exception;

public class TokenExpiredException extends TokenVerificationException {
  private static final String MESSAGE = "the token has expired";

  public TokenExpiredException(final Throwable cause) {
    super(MESSAGE, cause);
  }
}