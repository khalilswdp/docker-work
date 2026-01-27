package com.example.demo.core;

/** Null / missing configuration file or configuration root. */
public final class ConfigurationMissingException extends GilConfigurationException {

  public ConfigurationMissingException(String message) {
    super(message);
  }

  public ConfigurationMissingException(String message, Throwable cause) {
    super(message, cause);
  }
}