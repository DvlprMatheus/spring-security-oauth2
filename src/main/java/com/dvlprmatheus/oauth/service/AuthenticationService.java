package com.dvlprmatheus.oauth.service;

import com.dvlprmatheus.oauth.api.exception.AuthenticationFailedException;
import com.dvlprmatheus.oauth.api.exception.EmailConfirmationException;
import com.dvlprmatheus.oauth.api.exception.UserAlreadyExistsException;
import com.dvlprmatheus.oauth.api.exception.UserCreationException;
import com.dvlprmatheus.oauth.api.exception.UserNotExistsException;
import com.dvlprmatheus.oauth.api.request.AuthRequest;
import com.dvlprmatheus.oauth.api.request.ConfirmEmailRequest;
import com.dvlprmatheus.oauth.api.request.RefreshRequest;
import com.dvlprmatheus.oauth.api.request.RegisterRequest;
import com.dvlprmatheus.oauth.api.request.ResendCodeRequest;
import com.dvlprmatheus.oauth.api.response.AuthResponse;
import com.dvlprmatheus.oauth.entity.User;
import com.dvlprmatheus.oauth.service.aws.CognitoService;
import com.dvlprmatheus.oauth.util.LogSanitizer;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CodeMismatchException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ExpiredCodeException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidParameterException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.LimitExceededException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotConfirmedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;

@Slf4j
@AllArgsConstructor
@Service
public class AuthenticationService {

  private final UserService userService;
  private final CognitoService cognitoService;

  public AuthResponse authenticate(AuthRequest request) {
    log.info(
        "Attempting to authenticate user with email: {}", LogSanitizer.sanitize(request.email()));
    AuthenticationResultType result = authenticateInCognito(request);
    if (!userService.existsByEmail(request.email())) {
      log.warn("Email {} not found in database", LogSanitizer.sanitize(request.email()));
      throw new UserNotExistsException("User not found");
    }
    log.info(
        "User with email {} authenticated successfully", LogSanitizer.sanitize(request.email()));
    return toAuthResponse(result, result.refreshToken());
  }

  public AuthResponse refresh(RefreshRequest request) {
    log.info(
        "Attempting to refresh tokens for user with email: {}",
        LogSanitizer.sanitize(request.email()));
    User user = userService.findByEmail(request.email());
    try {
      AuthenticationResultType result =
          cognitoService.refresh(user.getCognitoSub(), request.refreshToken());
      log.info(
          "Tokens refreshed successfully for user with email: {}",
          LogSanitizer.sanitize(request.email()));
      return toAuthResponse(result, request.refreshToken());
    } catch (NotAuthorizedException e) {
      log.warn("Invalid refresh token for email: {}", LogSanitizer.sanitize(request.email()));
      throw new AuthenticationFailedException("Invalid refresh token", e);
    }
  }

  public void logout(Jwt jwt) {
    log.info(
        "Attempting to logout user with Cognito sub: {}", LogSanitizer.sanitize(jwt.getSubject()));
    try {
      cognitoService.globalSignOut(jwt.getTokenValue());
      log.info(
          "User with Cognito sub {} logged out successfully",
          LogSanitizer.sanitize(jwt.getSubject()));
    } catch (NotAuthorizedException e) {
      log.warn("Cognito access token already expired or revoked");
    }
  }

  private AuthenticationResultType authenticateInCognito(AuthRequest request) {
    AuthenticationResultType result;
    try {
      result = cognitoService.authenticate(request.email(), request.password());
    } catch (NotAuthorizedException | UserNotFoundException e) {
      log.warn("Invalid credentials for email: {}", LogSanitizer.sanitize(request.email()));
      throw new AuthenticationFailedException("Invalid email or password", e);
    } catch (UserNotConfirmedException e) {
      log.warn("User with email {} is not confirmed", LogSanitizer.sanitize(request.email()));
      throw new AuthenticationFailedException("User is not confirmed", e);
    } catch (Exception e) {
      log.error("Error authenticating user with email: {}", LogSanitizer.sanitize(request.email()));
      throw new AuthenticationFailedException("Error authenticating user", e);
    }
    if (result == null) {
      log.warn(
          "Additional authentication challenge required for email: {}",
          LogSanitizer.sanitize(request.email()));
      throw new AuthenticationFailedException("Additional authentication challenge required");
    }
    return result;
  }

  private AuthResponse toAuthResponse(AuthenticationResultType result, String refreshToken) {
    return new AuthResponse(
        result.accessToken(),
        result.idToken(),
        result.refreshToken() != null ? result.refreshToken() : refreshToken,
        result.tokenType(),
        result.expiresIn());
  }

  public void register(RegisterRequest request) {
    log.info(
        "Attempting to register user with username: {}", LogSanitizer.sanitize(request.username()));
    validateRegisterRequest(request);
    createUserInCognitoAndDatabase(request);
  }

  public void confirmEmail(ConfirmEmailRequest request) {
    log.info("Attempting to confirm email: {}", LogSanitizer.sanitize(request.email()));
    if (!userService.existsByEmail(request.email())) {
      log.warn("Email {} not found in database", LogSanitizer.sanitize(request.email()));
      throw new UserNotExistsException("User not found");
    }
    try {
      cognitoService.confirmSignUp(request.email(), request.code());
      log.info("Email {} confirmed successfully", LogSanitizer.sanitize(request.email()));
    } catch (CodeMismatchException e) {
      log.warn("Invalid confirmation code for email: {}", LogSanitizer.sanitize(request.email()));
      throw new EmailConfirmationException("Invalid confirmation code", e);
    } catch (ExpiredCodeException e) {
      log.warn("Expired confirmation code for email: {}", LogSanitizer.sanitize(request.email()));
      throw new EmailConfirmationException("Confirmation code expired", e);
    } catch (NotAuthorizedException e) {
      log.warn("Email {} is already confirmed", LogSanitizer.sanitize(request.email()));
      throw new EmailConfirmationException("Email is already confirmed", e);
    } catch (Exception e) {
      log.error("Error confirming email: {}", LogSanitizer.sanitize(request.email()));
      throw new EmailConfirmationException("Error confirming email", e);
    }
  }

  public void resendConfirmationCode(ResendCodeRequest request) {
    log.info(
        "Attempting to resend confirmation code to email: {}",
        LogSanitizer.sanitize(request.email()));
    if (!userService.existsByEmail(request.email())) {
      log.warn("Email {} not found in database", LogSanitizer.sanitize(request.email()));
      throw new UserNotExistsException("User not found");
    }
    try {
      cognitoService.resendConfirmationCode(request.email());
      log.info(
          "Confirmation code resent successfully to email: {}",
          LogSanitizer.sanitize(request.email()));
    } catch (InvalidParameterException e) {
      log.warn("Email {} is already confirmed", LogSanitizer.sanitize(request.email()));
      throw new EmailConfirmationException("Email is already confirmed", e);
    } catch (LimitExceededException e) {
      log.warn(
          "Resend confirmation code limit exceeded for email: {}",
          LogSanitizer.sanitize(request.email()));
      throw new EmailConfirmationException("Too many attempts, try again later", e);
    } catch (Exception e) {
      log.error(
          "Error resending confirmation code to email: {}", LogSanitizer.sanitize(request.email()));
      throw new EmailConfirmationException("Error resending confirmation code", e);
    }
  }

  private void validateRegisterRequest(RegisterRequest request) {
    if (userService.existsByUsername(request.username())) {
      log.warn("Username {} already exists", LogSanitizer.sanitize(request.username()));
      throw new UserAlreadyExistsException("Username already exists");
    }
    if (userService.existsByEmail(request.email())) {
      log.warn("Email {} already exists", LogSanitizer.sanitize(request.email()));
      throw new UserAlreadyExistsException("Email already exists");
    }
  }

  private void createUserInCognitoAndDatabase(RegisterRequest request) {
    log.info(
        "Attempting to create user in Cognito and database with username: {}",
        LogSanitizer.sanitize(request.username()));
    String cognitoSub = createUserInCognito(request);
    createUserInDatabase(request, cognitoSub);
  }

  private String createUserInCognito(RegisterRequest request) {
    try {
      String cognitoSub = cognitoService.signUp(request.email(), request.password()).userSub();
      log.info(
          "User with email {} created successfully in Cognito",
          LogSanitizer.sanitize(request.email()));
      return cognitoSub;
    } catch (Exception e) {
      log.error(
          "Error creating user in Cognito with email: {}", LogSanitizer.sanitize(request.email()));
      throw new UserCreationException("Error creating user in Cognito", e);
    }
  }

  private void createUserInDatabase(RegisterRequest request, String cognitoSub) {
    try {
      createUser(request, cognitoSub);
      log.info(
          "User with username {} created successfully in database",
          LogSanitizer.sanitize(request.username()));
    } catch (Exception e) {
      this.cognitoService.adminDeleteUser(request.email());
      log.error(
          "Error creating user in database with username: {}",
          LogSanitizer.sanitize(request.username()));
      throw new UserCreationException("Error creating user in database", e);
    }
  }

  private User createUser(RegisterRequest request, String cognitoSub) {
    log.info(
        "Creating user in database with username: {}", LogSanitizer.sanitize(request.username()));
    User user =
        User.builder()
            .username(request.username())
            .email(request.email())
            .cognitoSub(cognitoSub)
            .build();
    return userService.save(user);
  }
}
