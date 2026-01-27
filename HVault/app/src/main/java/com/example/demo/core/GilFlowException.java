package com.example.demo.core;

/**
 * Root for flow execution / payload / transformation / runtime data treatment errors.
 */
public abstract class GilFlowException extends GilEngineException {

  protected GilFlowException() {
    super();
  }

  protected GilFlowException(String message) {
    super(message);
  }

  protected GilFlowException(Throwable cause) {
    super(cause);
  }

  protected GilFlowException(String message, Throwable cause) {
    super(message, cause);
  }
}