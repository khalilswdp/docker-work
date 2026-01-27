package com.example.demo.exception.identity.sdk.exception;

import com.example.demo.core.GilBaseException;

public class IdentityException extends GilBaseException {
  public IdentityException(final String message) {
    super(message);
  }

  public IdentityException(final String message, final Throwable cause) {
    super(message, cause);
  }
}