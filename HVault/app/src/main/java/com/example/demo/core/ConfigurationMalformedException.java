package com.example.demo.core;

/** Configuration cannot be parsed / invalid structure / malformed document. */
public final class ConfigurationMalformedException extends GilConfigurationException {

  public ConfigurationMalformedException(String message) {
    super(message);
  }

  public ConfigurationMalformedException(String message, Throwable cause) {
    super(message, cause);
  }
}