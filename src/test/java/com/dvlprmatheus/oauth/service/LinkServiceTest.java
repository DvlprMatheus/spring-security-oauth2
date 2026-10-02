package com.dvlprmatheus.oauth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidPasswordException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

@ExtendWith(MockitoExtension.class)
class LinkServiceTest {

  private static final UUID USER_ID = UUID.fromString("7b1e4c1a-3f2d-4a8e-9c5b-2d6f8e0a1b2c");
  private static final String EMAIL = "joao@example.com";
  private static final String ACCESS_TOKEN = "access-token";
  private static final String FEDERATED_SUB = "federated-sub";
  private static final String LOCAL_SUB = "local-sub";
  private static final String FEDERATED_USERNAME = "Microsoft_abc";
  private static final CognitoIdentity MICROSOFT = new CognitoIdentity("ms-user", "Microsoft");
  private static final CognitoTokenResponse TOKENS =
      new CognitoTokenResponse(ACCESS_TOKEN, "id-token", "refresh-token", "Bearer", 3600);

  @Mock private UserService userService;
  @Mock private OAuthService oAuthService;
  @Mock private OAuthStateService oAuthStateService;
  @Mock private CognitoService cognitoService;
  @Mock private CognitoOAuthService cognitoOAuthService;
  @Mock private SsoProviderService ssoProviderService;
  private LinkService linkService;

  @BeforeEach
  void setUp() {
    linkService =
        new LinkService(
            userService,
            oAuthService,
            oAuthStateService,
            cognitoService,
            cognitoOAuthService,
            ssoProviderService);
  }

  @Test
  void shouldCreateLocalLoginAndReplaceCognitoSub() {
    User user = federatedUser();
    when(cognitoService.describeUser(ACCESS_TOKEN)).thenReturn(federatedCognitoUser(EMAIL));
    when(cognitoService.adminCreateUser(EMAIL)).thenReturn(localCognitoUser());

    linkService.createLocalLogin(user, ACCESS_TOKEN, "Str0ngPass!");

    verify(cognitoService).adminSetPermanentPassword(EMAIL, "Str0ngPass!");
    verify(cognitoService).adminDeleteUser(FEDERATED_USERNAME);
    verify(cognitoService).adminLinkProviderForUser(EMAIL, MICROSOFT);
    assertThat(user.getCognitoSub()).isEqualTo(LOCAL_SUB);
    verify(userService).save(user);
  }

  @Test
  void shouldRejectLocalLoginWhenUserAlreadyHasOne() {
    when(cognitoService.describeUser(ACCESS_TOKEN)).thenReturn(localCognitoUser());

    assertThatThrownBy(
            () -> linkService.createLocalLogin(federatedUser(), ACCESS_TOKEN, "Str0ngPass!"))
        .isInstanceOf(UserAlreadyExistsException.class)
        .hasMessage("Local login already exists");
    verify(cognitoService, never()).adminCreateUser(any());
  }

  @Test
  void shouldRejectLocalLoginWhenCognitoUsernameAlreadyExists() {
    when(cognitoService.describeUser(ACCESS_TOKEN)).thenReturn(federatedCognitoUser(EMAIL));
    when(cognitoService.adminCreateUser(EMAIL))
        .thenThrow(UsernameExistsException.builder().message("exists").build());

    assertThatThrownBy(
            () -> linkService.createLocalLogin(federatedUser(), ACCESS_TOKEN, "Str0ngPass!"))
        .isInstanceOf(UserAlreadyExistsException.class)
        .hasMessage("Local login already exists");
    verify(userService, never()).save(any());
  }

  @Test
  void shouldDeleteLocalUserWhenPasswordIsRejected() {
    when(cognitoService.describeUser(ACCESS_TOKEN)).thenReturn(federatedCognitoUser(EMAIL));
    when(cognitoService.adminCreateUser(EMAIL)).thenReturn(localCognitoUser());
    doThrow(InvalidPasswordException.builder().message("weak").build())
        .when(cognitoService)
        .adminSetPermanentPassword(EMAIL, "weak");

    assertThatThrownBy(() -> linkService.createLocalLogin(federatedUser(), ACCESS_TOKEN, "weak"))
        .isInstanceOf(AccountLinkException.class)
        .hasMessage("Password does not meet the requirements");
    verify(cognitoService).adminDeleteUser(EMAIL);
    verify(cognitoService, never()).adminLinkProviderForUser(any(), any());
    verify(userService, never()).save(any());
  }

  @Test
  void shouldDeleteLocalUserWhenFederatedUserCannotBeRemoved() {
    when(cognitoService.describeUser(ACCESS_TOKEN)).thenReturn(federatedCognitoUser(EMAIL));
    when(cognitoService.adminCreateUser(EMAIL)).thenReturn(localCognitoUser());
    doThrow(cognitoFailure()).when(cognitoService).adminDeleteUser(FEDERATED_USERNAME);

    assertThatThrownBy(
            () -> linkService.createLocalLogin(federatedUser(), ACCESS_TOKEN, "Str0ngPass!"))
        .isInstanceOf(UserCreationException.class)
        .hasMessage("Error linking local login");
    verify(cognitoService).adminDeleteUser(EMAIL);
    verify(userService, never()).save(any());
  }

  @Test
  void shouldClearCognitoSubWhenLinkingTheNewLocalUserFails() {
    User user = federatedUser();
    when(cognitoService.describeUser(ACCESS_TOKEN)).thenReturn(federatedCognitoUser(EMAIL));
    when(cognitoService.adminCreateUser(EMAIL)).thenReturn(localCognitoUser());
    doThrow(cognitoFailure()).when(cognitoService).adminLinkProviderForUser(EMAIL, MICROSOFT);

    assertThatThrownBy(() -> linkService.createLocalLogin(user, ACCESS_TOKEN, "Str0ngPass!"))
        .isInstanceOf(UserCreationException.class)
        .hasMessage("Error linking local login");
    assertThat(user.getCognitoSub()).isNull();
    verify(cognitoService).adminDeleteUser(EMAIL);
    verify(userService).save(user);
  }

  @Test
  void shouldBuildFederatedLinkUrlWithSignedState() {
    User user = localUser();
    when(ssoProviderService.findByType(SsoProviderType.GOOGLE)).thenReturn(google());
    when(cognitoService.adminFindUser(EMAIL)).thenReturn(Optional.of(localCognitoUser()));
    when(oAuthStateService.create(USER_ID, SsoProviderType.GOOGLE)).thenReturn("signed-state");
    when(cognitoOAuthService.authorizeUrl("Google", "signed-state")).thenReturn("https://link");

    assertThat(linkService.federatedLinkUrl(user, SsoProviderType.GOOGLE))
        .isEqualTo("https://link");
  }

  @Test
  void shouldRejectFederatedLinkWithoutLocalLogin() {
    when(ssoProviderService.findByType(SsoProviderType.GOOGLE)).thenReturn(google());
    when(cognitoService.adminFindUser(EMAIL)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> linkService.federatedLinkUrl(localUser(), SsoProviderType.GOOGLE))
        .isInstanceOf(AccountLinkException.class)
        .hasMessage("Create a local login before linking a provider");
    verify(cognitoOAuthService, never()).authorizeUrl(any(), any());
  }

  @Test
  void shouldLinkFederatedIdentityWhenEmailMatches() {
    User user = localUser();
    when(oAuthStateService.verify("signed"))
        .thenReturn(new LinkStateResponse(USER_ID, SsoProviderType.MICROSOFT));
    when(ssoProviderService.findByType(SsoProviderType.MICROSOFT)).thenReturn(microsoft());
    when(oAuthService.exchangeAuthorizationCode("code", null, null)).thenReturn(TOKENS);
    when(cognitoService.describeUser(ACCESS_TOKEN)).thenReturn(federatedCognitoUser(EMAIL));
    when(userService.findById(USER_ID)).thenReturn(user);
    when(cognitoService.adminFindUser(EMAIL)).thenReturn(Optional.of(localCognitoUser()));

    linkService.linkFederatedIdentity("code", "signed", null, null);

    verify(cognitoService).adminDeleteUser(FEDERATED_USERNAME);
    verify(cognitoService).adminLinkProviderForUser(EMAIL, MICROSOFT);
    verify(userService, never()).save(any());
  }

  @Test
  void shouldRejectLinkWhenProviderIsAlreadyLinked() {
    when(oAuthStateService.verify("signed"))
        .thenReturn(new LinkStateResponse(USER_ID, SsoProviderType.MICROSOFT));
    when(ssoProviderService.findByType(SsoProviderType.MICROSOFT)).thenReturn(microsoft());
    when(oAuthService.exchangeAuthorizationCode("code", null, null)).thenReturn(TOKENS);
    when(cognitoService.describeUser(ACCESS_TOKEN))
        .thenReturn(new CognitoUser(FEDERATED_USERNAME, LOCAL_SUB, EMAIL, List.of(MICROSOFT)));
    when(userService.findById(USER_ID)).thenReturn(localUser());

    assertThatThrownBy(() -> linkService.linkFederatedIdentity("code", "signed", null, null))
        .isInstanceOf(UserAlreadyExistsException.class)
        .hasMessage("Identity provider already linked");
    verify(cognitoService, never()).adminDeleteUser(any());
  }

  @Test
  void shouldRejectLinkWhenEmailsDiffer() {
    when(oAuthStateService.verify("signed"))
        .thenReturn(new LinkStateResponse(USER_ID, SsoProviderType.MICROSOFT));
    when(ssoProviderService.findByType(SsoProviderType.MICROSOFT)).thenReturn(microsoft());
    when(oAuthService.exchangeAuthorizationCode("code", null, null)).thenReturn(TOKENS);
    when(cognitoService.describeUser(ACCESS_TOKEN))
        .thenReturn(federatedCognitoUser("other@example.com"));
    when(userService.findById(USER_ID)).thenReturn(localUser());

    assertThatThrownBy(() -> linkService.linkFederatedIdentity("code", "signed", null, null))
        .isInstanceOf(AccountLinkException.class)
        .hasMessage("Identity provider email does not match the user email");
    verify(cognitoService, never()).adminDeleteUser(any());
    verify(cognitoService, never()).adminLinkProviderForUser(any(), any());
  }

  @Test
  void shouldRejectLinkWhenProviderNameDoesNotMatch() {
    when(oAuthStateService.verify("signed"))
        .thenReturn(new LinkStateResponse(USER_ID, SsoProviderType.MICROSOFT));
    when(ssoProviderService.findByType(SsoProviderType.MICROSOFT)).thenReturn(microsoft());
    when(oAuthService.exchangeAuthorizationCode("code", null, null)).thenReturn(TOKENS);
    when(cognitoService.describeUser(ACCESS_TOKEN))
        .thenReturn(
            new CognitoUser(
                "Google_abc",
                FEDERATED_SUB,
                EMAIL,
                List.of(new CognitoIdentity("g-user", "Google"))));
    when(userService.findById(USER_ID)).thenReturn(localUser());

    assertThatThrownBy(() -> linkService.linkFederatedIdentity("code", "signed", null, null))
        .isInstanceOf(AccountLinkException.class)
        .hasMessage("Unexpected identity provider");
    verify(cognitoService, never()).adminDeleteUser(any());
  }

  private static User federatedUser() {
    User user = User.builder().email(EMAIL).cognitoSub(FEDERATED_SUB).build();
    user.setId(USER_ID);
    return user;
  }

  private static User localUser() {
    User user = User.builder().email(EMAIL).cognitoSub(LOCAL_SUB).build();
    user.setId(USER_ID);
    return user;
  }

  private static CognitoUser federatedCognitoUser(String email) {
    return new CognitoUser(FEDERATED_USERNAME, FEDERATED_SUB, email, List.of(MICROSOFT));
  }

  private static CognitoUser localCognitoUser() {
    return new CognitoUser(EMAIL, LOCAL_SUB, EMAIL, List.of());
  }

  private static SsoProvider microsoft() {
    return SsoProvider.builder()
        .type(SsoProviderType.MICROSOFT)
        .identityProvider("Microsoft")
        .build();
  }

  private static SsoProvider google() {
    return SsoProvider.builder().type(SsoProviderType.GOOGLE).identityProvider("Google").build();
  }

  private static SdkException cognitoFailure() {
    return SdkClientException.create("cognito failure");
  }
}
