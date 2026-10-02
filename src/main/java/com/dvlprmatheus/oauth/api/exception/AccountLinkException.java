package com.dvlprmatheus.oauth.api.exception;

public class AccountLinkException extends RuntimeException {
  public AccountLinkException(String message) {
    super(message);
  }

  public AccountLinkException(String message, Throwable cause) {
    super(message, cause);
  }
}
