package com.dvlprmatheus.oauth.config.security;

import com.dvlprmatheus.oauth.entity.User;
import com.dvlprmatheus.oauth.service.UserService;
import com.dvlprmatheus.oauth.service.aws.CognitoService;
import com.dvlprmatheus.oauth.util.LogSanitizer;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;

@Slf4j
@AllArgsConstructor
@Component
public class CognitoJwtAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  private final UserService userService;
  private final CognitoService cognitoService;

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    ensureTokenNotRevoked(jwt);
    User user =
        userService
            .findByCognitoSubOptional(jwt.getSubject())
            .orElseThrow(
                () -> {
                  log.warn(
                      "User with Cognito sub {} not found in database",
                      LogSanitizer.sanitize(jwt.getSubject()));
                  return new UsernameNotFoundException("User not found");
                });
    return new CognitoAuthenticationToken(user, jwt);
  }

  private void ensureTokenNotRevoked(Jwt jwt) {
    try {
      cognitoService.getUser(jwt.getTokenValue());
    } catch (NotAuthorizedException e) {
      log.warn("Revoked access token for Cognito sub {}", LogSanitizer.sanitize(jwt.getSubject()));
      throw new InvalidBearerTokenException("Token has been revoked");
    } catch (SdkException e) {
      log.error("Error validating access token with Cognito");
      throw new AuthenticationServiceException("Unable to validate token", e);
    }
  }
}
