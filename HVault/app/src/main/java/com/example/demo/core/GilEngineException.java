package com.example.demo.core;

/** Marker base class for Core (Engine) exception family. */
public abstract class GilEngineException extends GilBaseException {

  protected GilEngineException() {
    super();
  }

  protected GilEngineException(String message) {
    super(message);
  }

  protected GilEngineException(String message, Throwable cause) {
    super(message, cause);
  }

  protected GilEngineException(Throwable cause) {
    super(cause);
  }
}