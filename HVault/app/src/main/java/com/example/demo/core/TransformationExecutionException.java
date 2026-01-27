package com.example.demo.core;

/** A transformation failed during execution (request/response/event transformation). */
public final class TransformationExecutionException extends GilFlowException {

  public TransformationExecutionException(String message) {
    super(message);
  }

  public TransformationExecutionException(String message, Throwable cause) {
    super(message, cause);
  }
}