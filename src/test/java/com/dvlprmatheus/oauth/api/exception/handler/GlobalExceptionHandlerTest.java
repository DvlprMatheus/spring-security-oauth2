package com.dvlprmatheus.oauth.api.exception.handler;

import static org.assertj.core.api.Assertions.assertThat;

import com.dvlprmatheus.oauth.api.exception.AccountLinkException;
import com.dvlprmatheus.oauth.api.exception.AuthenticationFailedException;
import com.dvlprmatheus.oauth.api.exception.EmailConfirmationException;
import com.dvlprmatheus.oauth.api.exception.SsoProviderNotFoundException;
import com.dvlprmatheus.oauth.api.exception.UserAlreadyExistsException;
import com.dvlprmatheus.oauth.api.exception.UserCreationException;
import com.dvlprmatheus.oauth.api.exception.UserNotExistsException;
import com.dvlprmatheus.oauth.api.response.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
  private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/test");

  private static void assertError(
      ResponseEntity<ErrorResponse> response, HttpStatus status, String message) {
    assertThat(response.getStatusCode()).isEqualTo(status);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getStatus()).isEqualTo(status.value());
    assertThat(response.getBody().getMessage()).isEqualTo(message);
    assertThat(response.getBody().getPath()).isEqualTo("/auth/test");
    assertThat(response.getBody().getTimestamp()).isNotNull();
  }

  @Test
  void shouldMapUserAlreadyExistsToConflict() {
    assertError(
        handler.handleUserAlreadyExistsException(
            new UserAlreadyExistsException("Email already exists"), request),
        HttpStatus.CONFLICT,
        "Email already exists");
  }

  @Test
  void shouldMapUserCreationToInternalServerError() {
    assertError(
        handler.handleUserCreationException(
            new UserCreationException("Error creating user in Cognito"), request),
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Error creating user in Cognito");
  }

  @Test
  void shouldMapAuthenticationFailedToUnauthorized() {
    assertError(
        handler.handleAuthenticationFailedException(
            new AuthenticationFailedException("Invalid email or password"), request),
        HttpStatus.UNAUTHORIZED,
        "Invalid email or password");
  }

  @Test
  void shouldMapEmailConfirmationToBadRequest() {
    assertError(
        handler.handleEmailConfirmationException(
            new EmailConfirmationException("Invalid confirmation code"), request),
        HttpStatus.BAD_REQUEST,
        "Invalid confirmation code");
  }

  @Test
  void shouldMapAccountLinkToBadRequest() {
    assertError(
        handler.handleAccountLinkException(
            new AccountLinkException("Identity provider email does not match the user email"),
            request),
        HttpStatus.BAD_REQUEST,
        "Identity provider email does not match the user email");
  }

  @Test
  void shouldMapMissingSsoProviderToNotFound() {
    assertError(
        handler.handleSsoProviderNotFoundException(
            new SsoProviderNotFoundException("Identity provider is not registered"), request),
        HttpStatus.NOT_FOUND,
        "Identity provider is not registered");
  }

  @Test
  void shouldMapUserNotExistsToNotFound() {
    assertError(
        handler.handleUserNotExistsException(new UserNotExistsException("User not found"), request),
        HttpStatus.NOT_FOUND,
        "User not found");
  }

  @Test
  void shouldJoinValidationMessagesWithoutRejectedValues() {
    BeanPropertyBindingResult bindingResult =
        new BeanPropertyBindingResult(new Object(), "request");
    bindingResult.addError(
        new FieldError("request", "email", "not-an-email", false, null, null, "Invalid email"));
    bindingResult.addError(
        new FieldError(
            "request",
            "password",
            "secret",
            false,
            null,
            null,
            "Password must be between 8 and 32 characters"));

    ResponseEntity<ErrorResponse> response =
        handler.handleMethodArgumentNotValidException(
            new MethodArgumentNotValidException((MethodParameter) null, bindingResult), request);

    assertError(
        response,
        HttpStatus.BAD_REQUEST,
        "Invalid email, Password must be between 8 and 32 characters");
    assertThat(response.getBody().getMessage()).doesNotContain("secret", "not-an-email");
  }
}
