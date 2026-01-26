package com.example.demo.exception.identity.sdk.authentication.exception;

public class JsonWebKeyException extends TokenVerificationException {
  public JsonWebKeyException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
