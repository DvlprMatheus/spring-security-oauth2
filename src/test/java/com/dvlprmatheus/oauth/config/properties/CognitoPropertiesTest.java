package com.dvlprmatheus.oauth.config.properties;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CognitoPropertiesTest {

  private final CognitoProperties properties =
      new CognitoProperties(
          "sa-east-1",
          "sa-east-1_Pool",
          "client-id",
          "client-secret",
          "https://cognito.example.com",
          "http://localhost:8080/oauth2/callback");

  @Test
  void shouldBuildIssuerUriFromRegionAndUserPool() {
    assertThat(properties.issuerUri())
        .isEqualTo("https://cognito-idp.sa-east-1.amazonaws.com/sa-east-1_Pool");
  }

  @Test
  void shouldBuildJwkSetUriFromIssuer() {
    assertThat(properties.jwkSetUri())
        .isEqualTo(
            "https://cognito-idp.sa-east-1.amazonaws.com/sa-east-1_Pool/.well-known/jwks.json");
  }
}
