package com.example.demo.exception.identity.sdk.authentication.exception;

public class TokenDecodeException extends TokenVerificationException {
  private static final String MESSAGE = "error decoding jwt";

  public TokenDecodeException(final Throwable cause) {
    super(MESSAGE, cause);
  }
}
