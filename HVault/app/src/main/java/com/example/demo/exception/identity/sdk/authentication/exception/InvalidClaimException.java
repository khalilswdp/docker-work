package com.example.demo.exception.identity.sdk.authentication.exception;

public class InvalidClaimException extends TokenVerificationException {
  private static final String MESSAGE = "the provided claims are invalid";

  public InvalidClaimException(final Throwable cause) {
    super(MESSAGE, cause);
  }
}