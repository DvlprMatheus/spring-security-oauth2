package com.dvlprmatheus.oauth.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws.cognito")
public record CognitoProperties(
    String region,
    String userPoolId,
    String clientId,
    String clientSecret,
    String domain,
    String redirectUri,
    String microsoftIdentityProvider) {

  public String issuerUri() {
    return "https://cognito-idp." + region + ".amazonaws.com/" + userPoolId;
  }

  public String jwkSetUri() {
    return issuerUri() + "/.well-known/jwks.json";
  }
}
