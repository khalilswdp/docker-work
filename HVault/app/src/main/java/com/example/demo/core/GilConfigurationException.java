package com.example.demo.core;

/**
 * Root for configuration-related errors (parsing, missing, invalid constraints, bad references...).
 */
public abstract class GilConfigurationException extends GilEngineException {

  protected GilConfigurationException() {
    super();
  }

  protected GilConfigurationException(String message) {
    super(message);
  }

  protected GilConfigurationException(Throwable cause) {
    super(cause);
  }

  protected GilConfigurationException(String message, Throwable cause) {
    super(message, cause);
  }
}