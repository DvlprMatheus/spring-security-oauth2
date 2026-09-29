package com.dvlprmatheus.oauth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CodeMismatchException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ExpiredCodeException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidParameterException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.LimitExceededException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotConfirmedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

  private static final String EMAIL = "joao@example.com";
  private static final String PASSWORD = "Str0ngPass!";
  private static final String COGNITO_SUB = "cognito-sub-123";

  @Mock private UserService userService;
  @Mock private CognitoService cognitoService;
  @InjectMocks private AuthenticationService authenticationService;

  private static AuthenticationResultType tokens(String refreshToken) {
    return AuthenticationResultType.builder()
        .accessToken("access-token")
        .idToken("id-token")
        .refreshToken(refreshToken)
        .tokenType("Bearer")
        .expiresIn(3600)
        .build();
  }

  @Nested
  class Authenticate {

    private final AuthRequest request = new AuthRequest(EMAIL, PASSWORD);

    @Test
    void shouldReturnTokensWhenCredentialsAreValidAndUserExists() {
      when(cognitoService.authenticate(EMAIL, PASSWORD)).thenReturn(tokens("refresh-token"));
      when(userService.existsByEmail(EMAIL)).thenReturn(true);

      AuthResponse response = authenticationService.authenticate(request);

      assertThat(response)
          .isEqualTo(new AuthResponse("access-token", "id-token", "refresh-token", "Bearer", 3600));
    }

    @Test
    void shouldFailWhenUserIsNotInDatabase() {
      when(cognitoService.authenticate(EMAIL, PASSWORD)).thenReturn(tokens("refresh-token"));
      when(userService.existsByEmail(EMAIL)).thenReturn(false);

      assertThatThrownBy(() -> authenticationService.authenticate(request))
          .isInstanceOf(UserNotExistsException.class)
          .hasMessage("User not found");
    }

    @Test
    void shouldFailWithInvalidCredentialsWhenCognitoRejectsPassword() {
      when(cognitoService.authenticate(EMAIL, PASSWORD))
          .thenThrow(NotAuthorizedException.builder().message("Incorrect").build());

      assertThatThrownBy(() -> authenticationService.authenticate(request))
          .isInstanceOf(AuthenticationFailedException.class)
          .hasMessage("Invalid email or password");
      verifyNoInteractions(userService);
    }

    @Test
    void shouldFailWithInvalidCredentialsWhenUserDoesNotExistInCognito() {
      when(cognitoService.authenticate(EMAIL, PASSWORD))
          .thenThrow(UserNotFoundException.builder().message("Not found").build());

      assertThatThrownBy(() -> authenticationService.authenticate(request))
          .isInstanceOf(AuthenticationFailedException.class)
          .hasMessage("Invalid email or password");
    }

    @Test
    void shouldFailWhenUserIsNotConfirmed() {
      when(cognitoService.authenticate(EMAIL, PASSWORD))
          .thenThrow(UserNotConfirmedException.builder().message("Not confirmed").build());

      assertThatThrownBy(() -> authenticationService.authenticate(request))
          .isInstanceOf(AuthenticationFailedException.class)
          .hasMessage("User is not confirmed");
    }

    @Test
    void shouldFailWhenCognitoRequiresAdditionalChallenge() {
      when(cognitoService.authenticate(EMAIL, PASSWORD)).thenReturn(null);

      assertThatThrownBy(() -> authenticationService.authenticate(request))
          .isInstanceOf(AuthenticationFailedException.class)
          .hasMessage("Additional authentication challenge required");
    }

    @Test
    void shouldWrapUnexpectedCognitoErrors() {
      when(cognitoService.authenticate(EMAIL, PASSWORD))
          .thenThrow(new IllegalStateException("boom"));

      assertThatThrownBy(() -> authenticationService.authenticate(request))
          .isInstanceOf(AuthenticationFailedException.class)
          .hasMessage("Error authenticating user");
    }
  }

  @Nested
  class Refresh {

    private final RefreshRequest request = new RefreshRequest(EMAIL, "refresh-token");

    @Test
    void shouldKeepOriginalRefreshTokenWhenCognitoDoesNotRotateIt() {
      when(userService.findByEmail(EMAIL))
          .thenReturn(User.builder().email(EMAIL).cognitoSub(COGNITO_SUB).build());
      when(cognitoService.refresh(COGNITO_SUB, "refresh-token")).thenReturn(tokens(null));

      AuthResponse response = authenticationService.refresh(request);

      assertThat(response.accessToken()).isEqualTo("access-token");
      assertThat(response.refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void shouldFailWhenRefreshTokenIsInvalid() {
      when(userService.findByEmail(EMAIL))
          .thenReturn(User.builder().email(EMAIL).cognitoSub(COGNITO_SUB).build());
      when(cognitoService.refresh(COGNITO_SUB, "refresh-token"))
          .thenThrow(NotAuthorizedException.builder().message("Invalid").build());

      assertThatThrownBy(() -> authenticationService.refresh(request))
          .isInstanceOf(AuthenticationFailedException.class)
          .hasMessage("Invalid refresh token");
    }

    @Test
    void shouldFailWhenUserIsNotInDatabase() {
      when(userService.findByEmail(EMAIL)).thenThrow(new UserNotExistsException("User not found"));

      assertThatThrownBy(() -> authenticationService.refresh(request))
          .isInstanceOf(UserNotExistsException.class);
      verifyNoInteractions(cognitoService);
    }
  }

  @Nested
  class Logout {

    private final Jwt jwt =
        Jwt.withTokenValue("access-token").header("alg", "none").subject(COGNITO_SUB).build();

    @Test
    void shouldSignOutGloballyWithAccessToken() {
      authenticationService.logout(jwt);

      verify(cognitoService).globalSignOut("access-token");
    }

    @Test
    void shouldIgnoreAlreadyRevokedToken() {
      doThrow(NotAuthorizedException.builder().message("Revoked").build())
          .when(cognitoService)
          .globalSignOut("access-token");

      authenticationService.logout(jwt);

      verify(cognitoService).globalSignOut("access-token");
    }
  }

  @Nested
  class Register {

    private final RegisterRequest request = new RegisterRequest("joao", EMAIL, PASSWORD);

    @Test
    void shouldCreateUserInCognitoAndDatabase() {
      when(userService.existsByUsername("joao")).thenReturn(false);
      when(userService.existsByEmail(EMAIL)).thenReturn(false);
      when(cognitoService.signUp(EMAIL, PASSWORD))
          .thenReturn(SignUpResponse.builder().userSub(COGNITO_SUB).build());

      authenticationService.register(request);

      ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
      verify(userService).save(captor.capture());
      assertThat(captor.getValue().getUsername()).isEqualTo("joao");
      assertThat(captor.getValue().getEmail()).isEqualTo(EMAIL);
      assertThat(captor.getValue().getCognitoSub()).isEqualTo(COGNITO_SUB);
    }

    @Test
    void shouldRejectDuplicatedUsername() {
      when(userService.existsByUsername("joao")).thenReturn(true);

      assertThatThrownBy(() -> authenticationService.register(request))
          .isInstanceOf(UserAlreadyExistsException.class)
          .hasMessage("Username already exists");
      verifyNoInteractions(cognitoService);
    }

    @Test
    void shouldRejectDuplicatedEmail() {
      when(userService.existsByUsername("joao")).thenReturn(false);
      when(userService.existsByEmail(EMAIL)).thenReturn(true);

      assertThatThrownBy(() -> authenticationService.register(request))
          .isInstanceOf(UserAlreadyExistsException.class)
          .hasMessage("Email already exists");
      verifyNoInteractions(cognitoService);
    }

    @Test
    void shouldNotSaveUserWhenCognitoSignUpFails() {
      when(userService.existsByUsername("joao")).thenReturn(false);
      when(userService.existsByEmail(EMAIL)).thenReturn(false);
      when(cognitoService.signUp(EMAIL, PASSWORD)).thenThrow(new IllegalStateException("boom"));

      assertThatThrownBy(() -> authenticationService.register(request))
          .isInstanceOf(UserCreationException.class)
          .hasMessage("Error creating user in Cognito");
      verify(userService, never()).save(any());
    }

    @Test
    void shouldRollbackCognitoUserWhenDatabaseSaveFails() {
      when(userService.existsByUsername("joao")).thenReturn(false);
      when(userService.existsByEmail(EMAIL)).thenReturn(false);
      when(cognitoService.signUp(EMAIL, PASSWORD))
          .thenReturn(SignUpResponse.builder().userSub(COGNITO_SUB).build());
      when(userService.save(any())).thenThrow(new IllegalStateException("db down"));

      assertThatThrownBy(() -> authenticationService.register(request))
          .isInstanceOf(UserCreationException.class)
          .hasMessage("Error creating user in database");
      verify(cognitoService).adminDeleteUser(EMAIL);
    }
  }

  @Nested
  class ConfirmEmail {

    private final ConfirmEmailRequest request = new ConfirmEmailRequest(EMAIL, "123456");

    @Test
    void shouldConfirmSignUpInCognito() {
      when(userService.existsByEmail(EMAIL)).thenReturn(true);

      authenticationService.confirmEmail(request);

      verify(cognitoService).confirmSignUp(EMAIL, "123456");
    }

    @Test
    void shouldFailWhenUserIsNotInDatabase() {
      when(userService.existsByEmail(EMAIL)).thenReturn(false);

      assertThatThrownBy(() -> authenticationService.confirmEmail(request))
          .isInstanceOf(UserNotExistsException.class);
      verifyNoInteractions(cognitoService);
    }

    @Test
    void shouldFailWhenCodeIsInvalid() {
      when(userService.existsByEmail(EMAIL)).thenReturn(true);
      doThrow(CodeMismatchException.builder().message("Mismatch").build())
          .when(cognitoService)
          .confirmSignUp(EMAIL, "123456");

      assertThatThrownBy(() -> authenticationService.confirmEmail(request))
          .isInstanceOf(EmailConfirmationException.class)
          .hasMessage("Invalid confirmation code");
    }

    @Test
    void shouldFailWhenCodeIsExpired() {
      when(userService.existsByEmail(EMAIL)).thenReturn(true);
      doThrow(ExpiredCodeException.builder().message("Expired").build())
          .when(cognitoService)
          .confirmSignUp(EMAIL, "123456");

      assertThatThrownBy(() -> authenticationService.confirmEmail(request))
          .isInstanceOf(EmailConfirmationException.class)
          .hasMessage("Confirmation code expired");
    }

    @Test
    void shouldFailWhenEmailIsAlreadyConfirmed() {
      when(userService.existsByEmail(EMAIL)).thenReturn(true);
      doThrow(NotAuthorizedException.builder().message("Already confirmed").build())
          .when(cognitoService)
          .confirmSignUp(EMAIL, "123456");

      assertThatThrownBy(() -> authenticationService.confirmEmail(request))
          .isInstanceOf(EmailConfirmationException.class)
          .hasMessage("Email is already confirmed");
    }
  }

  @Nested
  class ResendConfirmationCode {

    private final ResendCodeRequest request = new ResendCodeRequest(EMAIL);

    @Test
    void shouldResendCodeInCognito() {
      when(userService.existsByEmail(EMAIL)).thenReturn(true);

      authenticationService.resendConfirmationCode(request);

      verify(cognitoService).resendConfirmationCode(EMAIL);
    }

    @Test
    void shouldFailWhenUserIsNotInDatabase() {
      when(userService.existsByEmail(EMAIL)).thenReturn(false);

      assertThatThrownBy(() -> authenticationService.resendConfirmationCode(request))
          .isInstanceOf(UserNotExistsException.class);
      verifyNoInteractions(cognitoService);
    }

    @Test
    void shouldFailWhenEmailIsAlreadyConfirmed() {
      when(userService.existsByEmail(EMAIL)).thenReturn(true);
      doThrow(InvalidParameterException.builder().message("Already confirmed").build())
          .when(cognitoService)
          .resendConfirmationCode(EMAIL);

      assertThatThrownBy(() -> authenticationService.resendConfirmationCode(request))
          .isInstanceOf(EmailConfirmationException.class)
          .hasMessage("Email is already confirmed");
    }

    @Test
    void shouldFailWhenLimitIsExceeded() {
      when(userService.existsByEmail(EMAIL)).thenReturn(true);
      doThrow(LimitExceededException.builder().message("Limit").build())
          .when(cognitoService)
          .resendConfirmationCode(EMAIL);

      assertThatThrownBy(() -> authenticationService.resendConfirmationCode(request))
          .isInstanceOf(EmailConfirmationException.class)
          .hasMessage("Too many attempts, try again later");
    }
  }
}
