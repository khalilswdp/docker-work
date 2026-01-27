package com.example.demo.core;

/**
 * Configuration violates constraints (regex mismatch, invalid enum value, missing required field, etc.).
 * Use a clear message to describe the violated constraint.
 */
public final class ConfigurationConstraintViolationException extends GilConfigurationException {

  public ConfigurationConstraintViolationException(String message) {
    super(message);
  }

  public ConfigurationConstraintViolationException(String message, Throwable cause) {
    super(message, cause);
  }
}