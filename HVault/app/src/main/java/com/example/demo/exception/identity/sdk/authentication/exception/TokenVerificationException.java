package com.example.demo.exception.identity.sdk.authentication.exception;

import com.example.demo.exception.identity.sdk.exception.IdentityException;

public class TokenVerificationException extends IdentityException {
  public TokenVerificationException(final String message) {
    super(message);
  }

  public TokenVerificationException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
