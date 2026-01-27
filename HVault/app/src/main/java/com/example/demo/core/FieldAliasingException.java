package com.example.demo.core;

/** Failed to alias/rename/derive required fields during transformation or pre-processing. */
public final class FieldAliasingException extends GilFlowException {

  public FieldAliasingException(String message) {
    super(message);
  }

  public FieldAliasingException(String message, Throwable cause) {
    super(message, cause);
  }
}
