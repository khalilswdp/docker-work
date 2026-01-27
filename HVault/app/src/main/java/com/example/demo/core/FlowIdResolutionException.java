package com.example.demo.core;

/** Cannot resolve or determine a flow ID for the given payload. */
public final class FlowIdResolutionException extends GilFlowException {

  public FlowIdResolutionException(String message) {
    super(message);
  }

  public FlowIdResolutionException(String message, Throwable cause) {
    super(message, cause);
  }
}