package com.dvlprmatheus.oauth.service.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dvlprmatheus.oauth.config.properties.CognitoProperties;
import com.dvlprmatheus.oauth.service.aws.model.CognitoIdentity;
import com.dvlprmatheus.oauth.service.aws.model.CognitoUser;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminLinkProviderForUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ConfirmSignUpRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GlobalSignOutRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.MessageActionType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ResendConfirmationCodeRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;

@ExtendWith(MockitoExtension.class)
class CognitoServiceTest {

  private static final String CLIENT_ID = "client-id";
  private static final String CLIENT_SECRET = "client-secret";
  private static final String EMAIL = "joao@example.com";

  @Mock private CognitoIdentityProviderClient cognitoClient;
  private CognitoService cognitoService;

  @BeforeEach
  void setUp() {
    CognitoProperties properties =
        new CognitoProperties(
            "sa-east-1",
            "pool-id",
            CLIENT_ID,
            CLIENT_SECRET,
            "https://cognito.example.com",
            "http://localhost:8080/callback");
    cognitoService = new CognitoService(cognitoClient, properties);
  }

  private static String expectedSecretHash(String username) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(CLIENT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return Base64.getEncoder()
        .encodeToString(mac.doFinal((username + CLIENT_ID).getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void shouldSignUpWithEmailAsUsernameAndSecretHash() throws Exception {
    cognitoService.signUp(EMAIL, "Str0ngPass!");

    ArgumentCaptor<SignUpRequest> captor = ArgumentCaptor.forClass(SignUpRequest.class);
    verify(cognitoClient).signUp(captor.capture());
    SignUpRequest request = captor.getValue();
    assertThat(request.clientId()).isEqualTo(CLIENT_ID);
    assertThat(request.username()).isEqualTo(EMAIL);
    assertThat(request.password()).isEqualTo("Str0ngPass!");
    assertThat(request.secretHash()).isEqualTo(expectedSecretHash(EMAIL));
    assertThat(request.userAttributes())
        .containsExactly(AttributeType.builder().name("email").value(EMAIL).build());
  }

  @Test
  void shouldConfirmSignUpWithSecretHash() throws Exception {
    cognitoService.confirmSignUp(EMAIL, "123456");

    ArgumentCaptor<ConfirmSignUpRequest> captor =
        ArgumentCaptor.forClass(ConfirmSignUpRequest.class);
    verify(cognitoClient).confirmSignUp(captor.capture());
    assertThat(captor.getValue().username()).isEqualTo(EMAIL);
    assertThat(captor.getValue().confirmationCode()).isEqualTo("123456");
    assertThat(captor.getValue().secretHash()).isEqualTo(expectedSecretHash(EMAIL));
  }

  @Test
  void shouldResendConfirmationCodeWithSecretHash() throws Exception {
    cognitoService.resendConfirmationCode(EMAIL);

    ArgumentCaptor<ResendConfirmationCodeRequest> captor =
        ArgumentCaptor.forClass(ResendConfirmationCodeRequest.class);
    verify(cognitoClient).resendConfirmationCode(captor.capture());
    assertThat(captor.getValue().username()).isEqualTo(EMAIL);
    assertThat(captor.getValue().secretHash()).isEqualTo(expectedSecretHash(EMAIL));
  }

  @Test
  void shouldAuthenticateWithUserPasswordFlow() throws Exception {
    AuthenticationResultType result =
        AuthenticationResultType.builder().accessToken("access-token").build();
    when(cognitoClient.initiateAuth(any(InitiateAuthRequest.class)))
        .thenReturn(InitiateAuthResponse.builder().authenticationResult(result).build());

    assertThat(cognitoService.authenticate(EMAIL, "Str0ngPass!")).isEqualTo(result);

    ArgumentCaptor<InitiateAuthRequest> captor = ArgumentCaptor.forClass(InitiateAuthRequest.class);
    verify(cognitoClient).initiateAuth(captor.capture());
    assertThat(captor.getValue().authFlow()).isEqualTo(AuthFlowType.USER_PASSWORD_AUTH);
    assertThat(captor.getValue().authParameters())
        .containsEntry("USERNAME", EMAIL)
        .containsEntry("PASSWORD", "Str0ngPass!")
        .containsEntry("SECRET_HASH", expectedSecretHash(EMAIL));
  }

  @Test
  void shouldRefreshWithSecretHashOverCognitoSub() throws Exception {
    when(cognitoClient.initiateAuth(any(InitiateAuthRequest.class)))
        .thenReturn(InitiateAuthResponse.builder().build());

    cognitoService.refresh("cognito-sub", "refresh-token");

    ArgumentCaptor<InitiateAuthRequest> captor = ArgumentCaptor.forClass(InitiateAuthRequest.class);
    verify(cognitoClient).initiateAuth(captor.capture());
    assertThat(captor.getValue().authFlow()).isEqualTo(AuthFlowType.REFRESH_TOKEN_AUTH);
    assertThat(captor.getValue().authParameters())
        .containsEntry("REFRESH_TOKEN", "refresh-token")
        .containsEntry("SECRET_HASH", expectedSecretHash("cognito-sub"));
  }

  @Test
  void shouldGetUserWithAccessToken() {
    cognitoService.getUser("access-token");

    verify(cognitoClient).getUser(GetUserRequest.builder().accessToken("access-token").build());
  }

  @Test
  void shouldSignOutGloballyWithAccessToken() {
    cognitoService.globalSignOut("access-token");

    verify(cognitoClient)
        .globalSignOut(GlobalSignOutRequest.builder().accessToken("access-token").build());
  }

  @Test
  void shouldDeleteUserFromConfiguredPool() {
    cognitoService.adminDeleteUser(EMAIL);

    verify(cognitoClient)
        .adminDeleteUser(
            AdminDeleteUserRequest.builder().username(EMAIL).userPoolId("pool-id").build());
  }

  @Test
  void shouldDescribeFederatedUserFromAccessToken() {
    when(cognitoClient.getUser(any(GetUserRequest.class)))
        .thenReturn(
            GetUserResponse.builder()
                .username("Microsoft_abc")
                .userAttributes(
                    AttributeType.builder().name("sub").value("federated-sub").build(),
                    AttributeType.builder().name("email").value(EMAIL).build(),
                    AttributeType.builder()
                        .name("identities")
                        .value(
                            """
                            [{"userId":"ms-user","providerName":"Microsoft","primary":true}]
                            """)
                        .build())
                .build());

    CognitoUser user = cognitoService.describeUser("access-token");

    assertThat(user.username()).isEqualTo("Microsoft_abc");
    assertThat(user.sub()).isEqualTo("federated-sub");
    assertThat(user.email()).isEqualTo(EMAIL);
    assertThat(user.federatedIdentity()).contains(new CognitoIdentity("ms-user", "Microsoft"));
  }

  @Test
  void shouldCreateConfirmedUserWithoutSendingAnInvitation() {
    when(cognitoClient.adminCreateUser(any(AdminCreateUserRequest.class)))
        .thenReturn(
            AdminCreateUserResponse.builder()
                .user(
                    UserType.builder()
                        .username(EMAIL)
                        .attributes(
                            AttributeType.builder().name("sub").value("local-sub").build(),
                            AttributeType.builder().name("email").value(EMAIL).build())
                        .build())
                .build());

    CognitoUser user = cognitoService.adminCreateUser(EMAIL);

    assertThat(user.sub()).isEqualTo("local-sub");
    assertThat(user.federatedIdentity()).isEmpty();
    ArgumentCaptor<AdminCreateUserRequest> captor =
        ArgumentCaptor.forClass(AdminCreateUserRequest.class);
    verify(cognitoClient).adminCreateUser(captor.capture());
    assertThat(captor.getValue().username()).isEqualTo(EMAIL);
    assertThat(captor.getValue().messageAction()).isEqualTo(MessageActionType.SUPPRESS);
    assertThat(captor.getValue().userAttributes())
        .contains(
            AttributeType.builder().name("email").value(EMAIL).build(),
            AttributeType.builder().name("email_verified").value("true").build());
  }

  @Test
  void shouldSetPermanentPassword() {
    cognitoService.adminSetPermanentPassword(EMAIL, "Str0ngPass!");

    verify(cognitoClient)
        .adminSetUserPassword(
            AdminSetUserPasswordRequest.builder()
                .userPoolId("pool-id")
                .username(EMAIL)
                .password("Str0ngPass!")
                .permanent(true)
                .build());
  }

  @Test
  void shouldReturnEmptyWhenAdminUserDoesNotExist() {
    when(cognitoClient.adminGetUser(any(AdminGetUserRequest.class)))
        .thenThrow(UserNotFoundException.builder().message("missing").build());

    assertThat(cognitoService.adminFindUser(EMAIL)).isEmpty();
  }

  @Test
  void shouldFindAdminUserByUsername() {
    when(cognitoClient.adminGetUser(any(AdminGetUserRequest.class)))
        .thenReturn(
            AdminGetUserResponse.builder()
                .username(EMAIL)
                .userAttributes(AttributeType.builder().name("sub").value("local-sub").build())
                .build());

    Optional<CognitoUser> user = cognitoService.adminFindUser(EMAIL);

    assertThat(user).map(CognitoUser::sub).contains("local-sub");
  }

  @Test
  void shouldLinkProviderSubjectToLocalUser() {
    cognitoService.adminLinkProviderForUser(EMAIL, new CognitoIdentity("ms-user", "Microsoft"));

    ArgumentCaptor<AdminLinkProviderForUserRequest> captor =
        ArgumentCaptor.forClass(AdminLinkProviderForUserRequest.class);
    verify(cognitoClient).adminLinkProviderForUser(captor.capture());
    AdminLinkProviderForUserRequest request = captor.getValue();
    assertThat(request.userPoolId()).isEqualTo("pool-id");
    assertThat(request.destinationUser().providerName()).isEqualTo("Cognito");
    assertThat(request.destinationUser().providerAttributeValue()).isEqualTo(EMAIL);
    assertThat(request.sourceUser().providerName()).isEqualTo("Microsoft");
    assertThat(request.sourceUser().providerAttributeName()).isEqualTo("Cognito_Subject");
    assertThat(request.sourceUser().providerAttributeValue()).isEqualTo("ms-user");
  }
}
