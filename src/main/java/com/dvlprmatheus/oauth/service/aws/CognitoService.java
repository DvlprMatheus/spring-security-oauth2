package com.dvlprmatheus.oauth.service.aws;

import com.dvlprmatheus.oauth.config.properties.CognitoProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ConfirmSignUpRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GlobalSignOutRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ResendConfirmationCodeRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpResponse;

@Slf4j
@AllArgsConstructor
@Service
public class CognitoService {

  private final CognitoIdentityProviderClient cognitoClient;
  private final CognitoProperties cognitoProperties;

  public SignUpResponse signUp(String email, String password) {
    SignUpRequest request =
        SignUpRequest.builder()
            .clientId(cognitoProperties.clientId())
            .username(email)
            .password(password)
            .secretHash(secretHash(email))
            .userAttributes(AttributeType.builder().name("email").value(email).build())
            .build();
    return cognitoClient.signUp(request);
  }

  public void confirmSignUp(String email, String code) {
    ConfirmSignUpRequest request =
        ConfirmSignUpRequest.builder()
            .clientId(cognitoProperties.clientId())
            .username(email)
            .confirmationCode(code)
            .secretHash(secretHash(email))
            .build();
    cognitoClient.confirmSignUp(request);
  }

  public void resendConfirmationCode(String email) {
    ResendConfirmationCodeRequest request =
        ResendConfirmationCodeRequest.builder()
            .clientId(cognitoProperties.clientId())
            .username(email)
            .secretHash(secretHash(email))
            .build();
    cognitoClient.resendConfirmationCode(request);
  }

  public AuthenticationResultType authenticate(String email, String password) {
    InitiateAuthRequest request =
        InitiateAuthRequest.builder()
            .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
            .clientId(cognitoProperties.clientId())
            .authParameters(
                Map.of("USERNAME", email, "PASSWORD", password, "SECRET_HASH", secretHash(email)))
            .build();
    return cognitoClient.initiateAuth(request).authenticationResult();
  }

  public AuthenticationResultType refresh(String cognitoSub, String refreshToken) {
    InitiateAuthRequest request =
        InitiateAuthRequest.builder()
            .authFlow(AuthFlowType.REFRESH_TOKEN_AUTH)
            .clientId(cognitoProperties.clientId())
            .authParameters(
                Map.of("REFRESH_TOKEN", refreshToken, "SECRET_HASH", secretHash(cognitoSub)))
            .build();
    return cognitoClient.initiateAuth(request).authenticationResult();
  }

  public void getUser(String accessToken) {
    cognitoClient.getUser(GetUserRequest.builder().accessToken(accessToken).build());
  }

  public void globalSignOut(String accessToken) {
    GlobalSignOutRequest request = GlobalSignOutRequest.builder().accessToken(accessToken).build();
    cognitoClient.globalSignOut(request);
  }

  public void adminDeleteUser(String email) {
    log.info("Deleting user from Cognito");
    AdminDeleteUserRequest request =
        AdminDeleteUserRequest.builder()
            .username(email)
            .userPoolId(cognitoProperties.userPoolId())
            .build();
    cognitoClient.adminDeleteUser(request);
  }

  private String secretHash(String value) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(
          new SecretKeySpec(
              cognitoProperties.clientSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] hash =
          mac.doFinal((value + cognitoProperties.clientId()).getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(hash);
    } catch (Exception e) {
      throw new IllegalStateException("Error computing Cognito secret hash", e);
    }
  }
}
