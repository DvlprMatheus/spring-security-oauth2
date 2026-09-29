package com.dvlprmatheus.oauth.service;

import com.dvlprmatheus.oauth.api.exception.AuthenticationFailedException;
import com.dvlprmatheus.oauth.api.exception.UserAlreadyExistsException;
import com.dvlprmatheus.oauth.api.response.AuthResponse;
import com.dvlprmatheus.oauth.config.properties.CognitoProperties;
import com.dvlprmatheus.oauth.entity.User;
import com.dvlprmatheus.oauth.service.aws.CognitoOAuthService;
import com.dvlprmatheus.oauth.service.aws.model.CognitoTokenResponse;
import com.dvlprmatheus.oauth.service.aws.model.CognitoUserInfo;
import com.dvlprmatheus.oauth.util.LogSanitizer;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

@Slf4j
@AllArgsConstructor
@Service
public class OAuthService {

  private final UserService userService;
  private final CognitoOAuthService cognitoOAuthService;
  private final CognitoProperties cognitoProperties;

  public String microsoftAuthorizeUrl() {
    return cognitoOAuthService.authorizeUrl(cognitoProperties.microsoftIdentityProvider());
  }

  public AuthResponse authenticateWithMicrosoft(
      String code, String error, String errorDescription) {
    log.info("Attempting to authenticate user with Microsoft");
    if (error != null || code == null) {
      log.warn(
          "Microsoft login did not return an authorization code: error={}, description={}",
          LogSanitizer.sanitizeText(error),
          LogSanitizer.sanitizeText(errorDescription));
      throw new AuthenticationFailedException("Microsoft authentication failed");
    }
    CognitoTokenResponse tokens = exchangeAuthorizationCode(code);
    CognitoUserInfo userInfo = cognitoOAuthService.userInfo(tokens.accessToken());
    User user = findOrCreateFederatedUser(userInfo);
    log.info(
        "User with Cognito sub {} authenticated successfully with Microsoft",
        LogSanitizer.sanitize(user.getCognitoSub()));
    return new AuthResponse(
        tokens.accessToken(),
        tokens.idToken(),
        tokens.refreshToken(),
        tokens.tokenType(),
        tokens.expiresIn());
  }

  private CognitoTokenResponse exchangeAuthorizationCode(String code) {
    try {
      return cognitoOAuthService.exchangeAuthorizationCode(code);
    } catch (HttpClientErrorException e) {
      log.warn("Invalid or expired authorization code");
      throw new AuthenticationFailedException("Invalid or expired authorization code", e);
    } catch (RestClientException e) {
      log.error("Error exchanging authorization code with Cognito");
      throw new AuthenticationFailedException("Error authenticating with Microsoft", e);
    }
  }

  private User findOrCreateFederatedUser(CognitoUserInfo userInfo) {
    return userService
        .findByCognitoSubOptional(userInfo.sub())
        .orElseGet(() -> createFederatedUser(userInfo));
  }

  private User createFederatedUser(CognitoUserInfo userInfo) {
    if (userInfo.email() == null || userInfo.email().isBlank()) {
      log.warn(
          "Identity provider returned no email for Cognito sub {}",
          LogSanitizer.sanitize(userInfo.sub()));
      throw new AuthenticationFailedException("Identity provider did not return an email");
    }
    if (userService.existsByEmail(userInfo.email())) {
      log.warn(
          "Email {} already registered with password login",
          LogSanitizer.sanitize(userInfo.email()));
      throw new UserAlreadyExistsException("Email already registered, sign in with password");
    }
    log.info(
        "Creating federated user in database with email: {}",
        LogSanitizer.sanitize(userInfo.email()));
    return userService.save(
        User.builder().email(userInfo.email()).cognitoSub(userInfo.sub()).build());
  }
}
