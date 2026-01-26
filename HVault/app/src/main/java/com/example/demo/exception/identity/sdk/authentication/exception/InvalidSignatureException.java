package com.example.demo.exception.identity.sdk.authentication.exception;

public class InvalidSignatureException extends TokenVerificationException {
  private static final String MESSAGE = "the token signature is invalid";

  public InvalidSignatureException(final Throwable cause) {
    super(MESSAGE, cause);
  }
}
