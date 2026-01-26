package com.example.demo.exception.identity.sdk.exception;

public class IdentityException extends RuntimeException {
  public IdentityException(final String message) {
    super(message);
  }

  public IdentityException(final String message, final Throwable cause) {
    super(message, cause);
  }
}