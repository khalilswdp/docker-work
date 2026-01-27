package com.example.demo.core;

public final class FlowValidationException extends GilEngineException {
  public FlowValidationException(String message) { super(message); }
  public FlowValidationException(String message, Throwable cause) { super(message, cause); }
}