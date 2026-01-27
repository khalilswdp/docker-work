package com.example.demo.core;

/**
 * Common base for all GIL exceptions.
 * Intentionally minimal: behaves like RuntimeException.
 */
public class GilBaseException extends RuntimeException {

  public GilBaseException() {
    super();
  }

  public GilBaseException(String message) {
    super(message);
  }

  public GilBaseException(Throwable cause) {
    super(cause);
  }

  public GilBaseException(String message, Throwable cause) {
    super(message, cause);
  }
}