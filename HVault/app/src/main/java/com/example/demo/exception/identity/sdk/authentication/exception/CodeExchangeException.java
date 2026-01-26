package com.example.demo.exception.identity.sdk.authentication.exception;

import com.example.demo.exception.identity.sdk.exception.IdentityException;

public class CodeExchangeException extends IdentityException {
  public CodeExchangeException(final String message) {
    super(message);
  }

  public CodeExchangeException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
