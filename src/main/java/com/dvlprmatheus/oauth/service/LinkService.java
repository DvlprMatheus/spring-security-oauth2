package com.dvlprmatheus.oauth.service;

import com.dvlprmatheus.oauth.api.exception.AccountLinkException;
import com.dvlprmatheus.oauth.api.exception.UserAlreadyExistsException;
import com.dvlprmatheus.oauth.api.exception.UserCreationException;
import com.dvlprmatheus.oauth.api.response.LinkStateResponse;
import com.dvlprmatheus.oauth.entity.SsoProvider;
import com.dvlprmatheus.oauth.entity.User;
import com.dvlprmatheus.oauth.entity.enums.SsoProviderType;
import com.dvlprmatheus.oauth.service.aws.CognitoOAuthService;
import com.dvlprmatheus.oauth.service.aws.CognitoService;
import com.dvlprmatheus.oauth.service.aws.model.CognitoIdentity;
import com.dvlprmatheus.oauth.service.aws.model.CognitoTokenResponse;
import com.dvlprmatheus.oauth.service.aws.model.CognitoUser;
import com.dvlprmatheus.oauth.util.LogSanitizer;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidPasswordException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

@Slf4j
@AllArgsConstructor
@Service
public class LinkService {

  private final UserService userService;
  private final OAuthService oAuthService;
  private final OAuthStateService oAuthStateService;
  private final CognitoService cognitoService;
  private final CognitoOAuthService cognitoOAuthService;
  private final SsoProviderService ssoProviderService;

  public void createLocalLogin(User user, String accessToken, String password) {
    log.info("Creating local login for user with email {}", LogSanitizer.sanitize(user.getEmail()));
    CognitoUser federatedUser = cognitoService.describeUser(accessToken);
    CognitoIdentity identity =
        federatedUser
            .federatedIdentity()
            .orElseThrow(
                () -> {
                  log.warn(
                      "User with email {} already has a local login",
                      LogSanitizer.sanitize(user.getEmail()));
                  return new UserAlreadyExistsException("Local login already exists");
                });
    CognitoUser localUser = createLocalCognitoUser(user.getEmail(), password);
    deleteFederatedUser(federatedUser, localUser);
    linkIdentityToNewLocalUser(user, localUser, identity);
    user.setCognitoSub(localUser.sub());
    userService.save(user);
    log.info(
        "Local login created and linked to {} for user with email {}",
        identity.providerName(),
        LogSanitizer.sanitize(user.getEmail()));
  }

  public String federatedLinkUrl(User user, SsoProviderType provider) {
    log.info(
        "Starting {} link for user with email {}",
        provider,
        LogSanitizer.sanitize(user.getEmail()));
    SsoProvider ssoProvider = ssoProviderService.findByType(provider);
    findLocalCognitoUser(user);
    return cognitoOAuthService.authorizeUrl(
        ssoProvider.getIdentityProvider(), oAuthStateService.create(user.getId(), provider));
  }

  public void linkFederatedIdentity(
      String code, String state, String error, String errorDescription) {
    LinkStateResponse linkState = oAuthStateService.verify(state);
    SsoProvider ssoProvider = ssoProviderService.findByType(linkState.provider());
    CognitoTokenResponse tokens =
        oAuthService.exchangeAuthorizationCode(code, error, errorDescription);
    CognitoUser federatedUser = cognitoService.describeUser(tokens.accessToken());
    User user = userService.findById(linkState.userId());
    if (Objects.equals(federatedUser.sub(), user.getCognitoSub())) {
      log.warn(
          "{} is already linked to user with email {}",
          linkState.provider(),
          LogSanitizer.sanitize(user.getEmail()));
      throw new UserAlreadyExistsException("Identity provider already linked");
    }
    CognitoIdentity identity =
        federatedUser
            .federatedIdentity()
            .filter(
                candidate ->
                    candidate.providerName().equalsIgnoreCase(ssoProvider.getIdentityProvider()))
            .orElseThrow(() -> new AccountLinkException("Unexpected identity provider"));
    if (federatedUser.email() == null || !federatedUser.email().equalsIgnoreCase(user.getEmail())) {
      log.warn(
          "Identity provider email {} does not match user email {}",
          LogSanitizer.sanitize(federatedUser.email()),
          LogSanitizer.sanitize(user.getEmail()));
      throw new AccountLinkException("Identity provider email does not match the user email");
    }
    CognitoUser localUser = findLocalCognitoUser(user);
    try {
      cognitoService.adminDeleteUser(federatedUser.username());
      cognitoService.adminLinkProviderForUser(localUser.username(), identity);
    } catch (SdkException e) {
      log.error(
          "Error linking {} to user with email {}",
          identity.providerName(),
          LogSanitizer.sanitize(user.getEmail()));
      throw new UserCreationException("Error linking identity provider", e);
    }
    log.info(
        "{} linked to user with email {}",
        identity.providerName(),
        LogSanitizer.sanitize(user.getEmail()));
  }

  private CognitoUser findLocalCognitoUser(User user) {
    return cognitoService
        .adminFindUser(user.getEmail())
        .filter(localUser -> Objects.equals(localUser.sub(), user.getCognitoSub()))
        .orElseThrow(
            () -> {
              log.warn(
                  "User with email {} has no local login", LogSanitizer.sanitize(user.getEmail()));
              return new AccountLinkException("Create a local login before linking a provider");
            });
  }

  private CognitoUser createLocalCognitoUser(String email, String password) {
    CognitoUser localUser;
    try {
      localUser = cognitoService.adminCreateUser(email);
    } catch (UsernameExistsException e) {
      log.warn("Local login already exists for email {}", LogSanitizer.sanitize(email));
      throw new UserAlreadyExistsException("Local login already exists", e);
    } catch (SdkException e) {
      log.error("Error creating local login for email {}", LogSanitizer.sanitize(email));
      throw new UserCreationException("Error creating local login", e);
    }
    try {
      cognitoService.adminSetPermanentPassword(localUser.username(), password);
      return localUser;
    } catch (InvalidPasswordException e) {
      cognitoService.adminDeleteUser(localUser.username());
      log.warn("Password rejected by Cognito policy for email {}", LogSanitizer.sanitize(email));
      throw new AccountLinkException("Password does not meet the requirements", e);
    } catch (SdkException e) {
      cognitoService.adminDeleteUser(localUser.username());
      log.error("Error setting password for email {}", LogSanitizer.sanitize(email));
      throw new UserCreationException("Error creating local login", e);
    }
  }

  private void deleteFederatedUser(CognitoUser federatedUser, CognitoUser localUser) {
    try {
      cognitoService.adminDeleteUser(federatedUser.username());
    } catch (SdkException e) {
      cognitoService.adminDeleteUser(localUser.username());
      log.error("Error deleting federated user before linking");
      throw new UserCreationException("Error linking local login", e);
    }
  }

  private void linkIdentityToNewLocalUser(
      User user, CognitoUser localUser, CognitoIdentity identity) {
    try {
      cognitoService.adminLinkProviderForUser(localUser.username(), identity);
    } catch (SdkException e) {
      cognitoService.adminDeleteUser(localUser.username());
      user.setCognitoSub(null);
      userService.save(user);
      log.error(
          "Error linking {} to new local login for email {}",
          identity.providerName(),
          LogSanitizer.sanitize(user.getEmail()));
      throw new UserCreationException("Error linking local login", e);
    }
  }
}
