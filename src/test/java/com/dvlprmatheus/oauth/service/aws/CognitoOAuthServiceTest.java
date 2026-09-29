package com.dvlprmatheus.oauth.service.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.dvlprmatheus.oauth.config.properties.CognitoProperties;
import com.dvlprmatheus.oauth.service.aws.model.CognitoTokenResponse;
import com.dvlprmatheus.oauth.service.aws.model.CognitoUserInfo;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

class CognitoOAuthServiceTest {

  private static final String DOMAIN = "https://cognito.example.com";
  private static final String REDIRECT_URI = "http://localhost:8080/oauth2/microsoft/callback";

  private MockRestServiceServer server;
  private CognitoOAuthService cognitoOAuthService;

  @BeforeEach
  void setUp() {
    CognitoProperties properties =
        new CognitoProperties(
            "sa-east-1",
            "pool-id",
            "client-id",
            "client-secret",
            DOMAIN,
            REDIRECT_URI,
            "Microsoft");
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    cognitoOAuthService = new CognitoOAuthService(properties, builder);
  }

  @Test
  void shouldBuildAuthorizeUrlForIdentityProvider() {
    String url = cognitoOAuthService.authorizeUrl("Microsoft");

    assertThat(url)
        .startsWith(DOMAIN + "/oauth2/authorize?")
        .contains("response_type=code")
        .contains("client_id=client-id")
        .contains("redirect_uri=" + REDIRECT_URI)
        .contains("identity_provider=Microsoft")
        .contains("scope=openid%20email%20profile%20aws.cognito.signin.user.admin")
        .doesNotContain("client-secret");
  }

  @Test
  void shouldExchangeAuthorizationCodeUsingClientSecretBasicAuth() {
    String basicAuth =
        Base64.getEncoder()
            .encodeToString("client-id:client-secret".getBytes(StandardCharsets.UTF_8));
    server
        .expect(requestTo(DOMAIN + "/oauth2/token"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth))
        .andExpect(
            content()
                .formDataContains(
                    Map.of(
                        "grant_type", "authorization_code",
                        "code", "authorization-code",
                        "redirect_uri", REDIRECT_URI)))
        .andRespond(
            withSuccess(
                """
                {"access_token":"access-token","id_token":"id-token",
                 "refresh_token":"refresh-token","token_type":"Bearer","expires_in":3600}
                """,
                MediaType.APPLICATION_JSON));

    CognitoTokenResponse tokens =
        cognitoOAuthService.exchangeAuthorizationCode("authorization-code");

    assertThat(tokens)
        .isEqualTo(
            new CognitoTokenResponse("access-token", "id-token", "refresh-token", "Bearer", 3600));
    server.verify();
  }

  @Test
  void shouldPropagateClientErrorWhenCodeIsInvalid() {
    server
        .expect(requestTo(DOMAIN + "/oauth2/token"))
        .andRespond(
            withBadRequest()
                .body("{\"error\":\"invalid_grant\"}")
                .contentType(MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> cognitoOAuthService.exchangeAuthorizationCode("expired-code"))
        .isInstanceOf(HttpClientErrorException.class);
  }

  @Test
  void shouldFetchUserInfoWithBearerTokenIgnoringUnknownFields() {
    server
        .expect(requestTo(DOMAIN + "/oauth2/userInfo"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
        .andRespond(
            withSuccess(
                """
                {"sub":"cognito-sub","email":"joao@example.com",
                 "email_verified":"true","username":"Microsoft_abc"}
                """,
                MediaType.APPLICATION_JSON));

    CognitoUserInfo userInfo = cognitoOAuthService.userInfo("access-token");

    assertThat(userInfo).isEqualTo(new CognitoUserInfo("cognito-sub", "joao@example.com"));
    server.verify();
  }
}
