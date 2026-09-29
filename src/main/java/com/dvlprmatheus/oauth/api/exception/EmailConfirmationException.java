package com.dvlprmatheus.oauth.api.exception;

public class EmailConfirmationException extends RuntimeException {
  public EmailConfirmationException(String message) {
    super(message);
  }

  public EmailConfirmationException(String message, Throwable cause) {
    super(message, cause);
  }
}
