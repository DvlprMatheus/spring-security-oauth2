package com.dvlprmatheus.oauth.service.aws;

import com.dvlprmatheus.oauth.config.properties.CognitoProperties;
import com.dvlprmatheus.oauth.service.aws.model.CognitoTokenResponse;
import com.dvlprmatheus.oauth.service.aws.model.CognitoUserInfo;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class CognitoOAuthService {

  private static final String SCOPES = "openid email profile aws.cognito.signin.user.admin";

  private final CognitoProperties cognitoProperties;
  private final RestClient restClient;

  @Autowired
  public CognitoOAuthService(CognitoProperties cognitoProperties) {
    this(cognitoProperties, RestClient.builder());
  }

  CognitoOAuthService(CognitoProperties cognitoProperties, RestClient.Builder restClientBuilder) {
    this.cognitoProperties = cognitoProperties;
    this.restClient = restClientBuilder.baseUrl(cognitoProperties.domain()).build();
  }

  public String authorizeUrl(String identityProvider) {
    return authorizeUrl(identityProvider, null);
  }

  public String authorizeUrl(String identityProvider, String state) {
    return UriComponentsBuilder.fromUriString(cognitoProperties.domain())
        .path("/oauth2/authorize")
        .queryParam("response_type", "code")
        .queryParam("client_id", cognitoProperties.clientId())
        .queryParam("redirect_uri", cognitoProperties.redirectUri())
        .queryParam("identity_provider", identityProvider)
        .queryParam("scope", SCOPES)
        .queryParam("prompt", "login")
        .queryParamIfPresent("state", Optional.ofNullable(state))
        .build()
        .encode()
        .toUriString();
  }

  public CognitoTokenResponse exchangeAuthorizationCode(String code) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "authorization_code");
    form.add("code", code);
    form.add("redirect_uri", cognitoProperties.redirectUri());
    return restClient
        .post()
        .uri("/oauth2/token")
        .headers(
            headers ->
                headers.setBasicAuth(
                    cognitoProperties.clientId(), cognitoProperties.clientSecret()))
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(form)
        .retrieve()
        .body(CognitoTokenResponse.class);
  }

  public CognitoUserInfo userInfo(String accessToken) {
    return restClient
        .get()
        .uri("/oauth2/userInfo")
        .headers(headers -> headers.setBearerAuth(accessToken))
        .retrieve()
        .body(CognitoUserInfo.class);
  }
}
