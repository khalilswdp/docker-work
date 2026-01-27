package com.example.demo.core;

/**
 * Payload is invalid: missing required fields, invalid format, schema mismatch, null mandatory values, etc.
 */
public final class InvalidPayloadException extends GilFlowException {

  public InvalidPayloadException(String message) {
    super(message);
  }

  public InvalidPayloadException(String message, Throwable cause) {
    super(message, cause);
  }
}