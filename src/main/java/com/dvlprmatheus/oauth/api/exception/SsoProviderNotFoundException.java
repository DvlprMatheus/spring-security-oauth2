package com.dvlprmatheus.oauth.api.exception;

public class SsoProviderNotFoundException extends RuntimeException {
  public SsoProviderNotFoundException(String message) {
    super(message);
  }

  public SsoProviderNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
