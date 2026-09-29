package com.dvlprmatheus.oauth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dvlprmatheus.oauth.api.exception.AuthenticationFailedException;
import com.dvlprmatheus.oauth.api.exception.UserAlreadyExistsException;
import com.dvlprmatheus.oauth.api.response.AuthResponse;
import com.dvlprmatheus.oauth.config.properties.CognitoProperties;
import com.dvlprmatheus.oauth.entity.User;
import com.dvlprmatheus.oauth.service.aws.CognitoOAuthService;
import com.dvlprmatheus.oauth.service.aws.model.CognitoTokenResponse;
import com.dvlprmatheus.oauth.service.aws.model.CognitoUserInfo;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

@ExtendWith(MockitoExtension.class)
class OAuthServiceTest {

  private static final String CODE = "authorization-code";
  private static final String COGNITO_SUB = "cognito-sub-123";
  private static final String EMAIL = "joao@example.com";
  private static final CognitoTokenResponse TOKENS =
      new CognitoTokenResponse("access-token", "id-token", "refresh-token", "Bearer", 3600);

  @Mock private UserService userService;
  @Mock private CognitoOAuthService cognitoOAuthService;
  private OAuthService oAuthService;

  @BeforeEach
  void setUp() {
    CognitoProperties properties =
        new CognitoProperties(
            "sa-east-1",
            "pool-id",
            "client-id",
            "client-secret",
            "https://cognito.example.com",
            "http://localhost:8080/oauth2/microsoft/callback",
            "Microsoft");
    oAuthService = new OAuthService(userService, cognitoOAuthService, properties);
  }

  @Test
  void shouldBuildAuthorizeUrlWithConfiguredIdentityProvider() {
    when(cognitoOAuthService.authorizeUrl("Microsoft")).thenReturn("https://authorize");

    assertThat(oAuthService.microsoftAuthorizeUrl()).isEqualTo("https://authorize");
  }

  @Test
  void shouldFailWhenProviderReturnsError() {
    assertThatThrownBy(
            () ->
                oAuthService.authenticateWithMicrosoft(
                    null, "access_denied", "AADSTS50020: User account 'joao@example.com'"))
        .isInstanceOf(AuthenticationFailedException.class)
        .hasMessage("Microsoft authentication failed");
    verifyNoInteractions(cognitoOAuthService, userService);
  }

  @Test
  void shouldFailWhenCodeIsMissing() {
    assertThatThrownBy(() -> oAuthService.authenticateWithMicrosoft(null, null, null))
        .isInstanceOf(AuthenticationFailedException.class)
        .hasMessage("Microsoft authentication failed");
    verifyNoInteractions(cognitoOAuthService, userService);
  }

  @Test
  void shouldFailWhenCodeIsRejectedByCognito() {
    when(cognitoOAuthService.exchangeAuthorizationCode(CODE))
        .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

    assertThatThrownBy(() -> oAuthService.authenticateWithMicrosoft(CODE, null, null))
        .isInstanceOf(AuthenticationFailedException.class)
        .hasMessage("Invalid or expired authorization code");
  }

  @Test
  void shouldFailWhenCognitoIsUnreachable() {
    when(cognitoOAuthService.exchangeAuthorizationCode(CODE))
        .thenThrow(new ResourceAccessException("timeout"));

    assertThatThrownBy(() -> oAuthService.authenticateWithMicrosoft(CODE, null, null))
        .isInstanceOf(AuthenticationFailedException.class)
        .hasMessage("Error authenticating with Microsoft");
  }

  @Test
  void shouldReturnTokensForExistingUserWithoutCreatingIt() {
    when(cognitoOAuthService.exchangeAuthorizationCode(CODE)).thenReturn(TOKENS);
    when(cognitoOAuthService.userInfo("access-token"))
        .thenReturn(new CognitoUserInfo(COGNITO_SUB, EMAIL));
    when(userService.findByCognitoSubOptional(COGNITO_SUB))
        .thenReturn(Optional.of(User.builder().email(EMAIL).cognitoSub(COGNITO_SUB).build()));

    AuthResponse response = oAuthService.authenticateWithMicrosoft(CODE, null, null);

    assertThat(response)
        .isEqualTo(new AuthResponse("access-token", "id-token", "refresh-token", "Bearer", 3600));
    verify(userService, never()).save(any());
  }

  @Test
  void shouldCreateFederatedUserOnFirstLogin() {
    when(cognitoOAuthService.exchangeAuthorizationCode(CODE)).thenReturn(TOKENS);
    when(cognitoOAuthService.userInfo("access-token"))
        .thenReturn(new CognitoUserInfo(COGNITO_SUB, EMAIL));
    when(userService.findByCognitoSubOptional(COGNITO_SUB)).thenReturn(Optional.empty());
    when(userService.existsByEmail(EMAIL)).thenReturn(false);
    when(userService.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    oAuthService.authenticateWithMicrosoft(CODE, null, null);

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userService).save(captor.capture());
    assertThat(captor.getValue().getEmail()).isEqualTo(EMAIL);
    assertThat(captor.getValue().getCognitoSub()).isEqualTo(COGNITO_SUB);
    assertThat(captor.getValue().getUsername()).isNull();
  }

  @Test
  void shouldFailWhenProviderDoesNotReturnEmail() {
    when(cognitoOAuthService.exchangeAuthorizationCode(CODE)).thenReturn(TOKENS);
    when(cognitoOAuthService.userInfo("access-token"))
        .thenReturn(new CognitoUserInfo(COGNITO_SUB, " "));
    when(userService.findByCognitoSubOptional(COGNITO_SUB)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> oAuthService.authenticateWithMicrosoft(CODE, null, null))
        .isInstanceOf(AuthenticationFailedException.class)
        .hasMessage("Identity provider did not return an email");
    verify(userService, never()).save(any());
  }

  @Test
  void shouldRejectEmailAlreadyRegisteredWithPassword() {
    when(cognitoOAuthService.exchangeAuthorizationCode(CODE)).thenReturn(TOKENS);
    when(cognitoOAuthService.userInfo("access-token"))
        .thenReturn(new CognitoUserInfo(COGNITO_SUB, EMAIL));
    when(userService.findByCognitoSubOptional(COGNITO_SUB)).thenReturn(Optional.empty());
    when(userService.existsByEmail(EMAIL)).thenReturn(true);

    assertThatThrownBy(() -> oAuthService.authenticateWithMicrosoft(CODE, null, null))
        .isInstanceOf(UserAlreadyExistsException.class)
        .hasMessage("Email already registered, sign in with password");
    verify(userService, never()).save(any());
  }
}
