package com.example.demo.core;

/**
 * Generic exception for when you prefer: (enum code + message) instead of a dedicated class.
 * Does NOT force changes in existing typed exceptions.
 */
public final class GilCodedException extends GilBaseException {
  private final GilErrorCode code;

  public GilCodedException(GilErrorCode code, String message) {
    super(message);
    this.code = code;
  }

  public GilCodedException(GilErrorCode code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  public GilErrorCode code() {
    return code;
  }
}