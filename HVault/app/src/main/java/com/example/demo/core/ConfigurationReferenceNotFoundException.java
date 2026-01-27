package com.example.demo.core;

/** Imported/referenced configuration entity not found (missing ID, missing include, missing flow ref...). */
public final class ConfigurationReferenceNotFoundException extends GilConfigurationException {

  public ConfigurationReferenceNotFoundException(String message) {
    super(message);
  }

  public ConfigurationReferenceNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}