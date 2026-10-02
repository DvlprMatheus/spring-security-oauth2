package com.dvlprmatheus.oauth.service.aws;

import com.dvlprmatheus.oauth.config.properties.CognitoProperties;
import com.dvlprmatheus.oauth.service.aws.model.CognitoIdentity;
import com.dvlprmatheus.oauth.service.aws.model.CognitoUser;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
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
import software.amazon.awssdk.services.cognitoidentityprovider.model.MessageActionType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ProviderUserIdentifierType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ResendConfirmationCodeRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@AllArgsConstructor
@Service
public class CognitoService {

  private static final JsonMapper JSON_MAPPER =
      JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
  private static final String LOCAL_PROVIDER = "Cognito";
  private static final String SUBJECT_ATTRIBUTE = "Cognito_Subject";

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

  public void adminDeleteUser(String username) {
    log.info("Deleting user from Cognito");
    AdminDeleteUserRequest request =
        AdminDeleteUserRequest.builder()
            .username(username)
            .userPoolId(cognitoProperties.userPoolId())
            .build();
    cognitoClient.adminDeleteUser(request);
  }

  public CognitoUser describeUser(String accessToken) {
    GetUserResponse response =
        cognitoClient.getUser(GetUserRequest.builder().accessToken(accessToken).build());
    return toCognitoUser(response.username(), response.userAttributes());
  }

  public CognitoUser adminCreateUser(String email) {
    AdminCreateUserRequest request =
        AdminCreateUserRequest.builder()
            .userPoolId(cognitoProperties.userPoolId())
            .username(email)
            .userAttributes(attribute("email", email), attribute("email_verified", "true"))
            .messageAction(MessageActionType.SUPPRESS)
            .build();
    UserType user = cognitoClient.adminCreateUser(request).user();
    return toCognitoUser(user.username(), user.attributes());
  }

  public void adminSetPermanentPassword(String username, String password) {
    AdminSetUserPasswordRequest request =
        AdminSetUserPasswordRequest.builder()
            .userPoolId(cognitoProperties.userPoolId())
            .username(username)
            .password(password)
            .permanent(true)
            .build();
    cognitoClient.adminSetUserPassword(request);
  }

  public Optional<CognitoUser> adminFindUser(String username) {
    AdminGetUserRequest request =
        AdminGetUserRequest.builder()
            .userPoolId(cognitoProperties.userPoolId())
            .username(username)
            .build();
    try {
      AdminGetUserResponse response = cognitoClient.adminGetUser(request);
      return Optional.of(toCognitoUser(response.username(), response.userAttributes()));
    } catch (UserNotFoundException e) {
      return Optional.empty();
    }
  }

  public void adminLinkProviderForUser(String destinationUsername, CognitoIdentity identity) {
    AdminLinkProviderForUserRequest request =
        AdminLinkProviderForUserRequest.builder()
            .userPoolId(cognitoProperties.userPoolId())
            .destinationUser(
                ProviderUserIdentifierType.builder()
                    .providerName(LOCAL_PROVIDER)
                    .providerAttributeValue(destinationUsername)
                    .build())
            .sourceUser(
                ProviderUserIdentifierType.builder()
                    .providerName(identity.providerName())
                    .providerAttributeName(SUBJECT_ATTRIBUTE)
                    .providerAttributeValue(identity.userId())
                    .build())
            .build();
    cognitoClient.adminLinkProviderForUser(request);
  }

  private static AttributeType attribute(String name, String value) {
    return AttributeType.builder().name(name).value(value).build();
  }

  private static CognitoUser toCognitoUser(String username, List<AttributeType> attributes) {
    List<AttributeType> present = attributes == null ? List.of() : attributes;
    Map<String, String> values =
        present.stream()
            .filter(attribute -> attribute.value() != null)
            .collect(
                Collectors.toMap(
                    AttributeType::name, AttributeType::value, (left, right) -> right));
    return new CognitoUser(
        username, values.get("sub"), values.get("email"), identities(values.get("identities")));
  }

  private static List<CognitoIdentity> identities(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    List<CognitoIdentity> identities =
        JSON_MAPPER.readValue(json, new TypeReference<List<CognitoIdentity>>() {});
    return identities == null ? List.of() : identities;
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
