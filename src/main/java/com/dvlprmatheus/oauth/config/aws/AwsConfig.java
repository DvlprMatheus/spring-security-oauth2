package com.dvlprmatheus.oauth.config.aws;

import com.dvlprmatheus.oauth.config.properties.CognitoProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

@Configuration
@EnableConfigurationProperties(CognitoProperties.class)
public class AwsConfig {

  @Bean
  public CognitoIdentityProviderClient cognitoIdentityProviderClient(
      CognitoProperties cognitoProperties) {
    return CognitoIdentityProviderClient.builder()
        .region(Region.of(cognitoProperties.region()))
        .credentialsProvider(AnonymousCredentialsProvider.create())
        .httpClient(ApacheHttpClient.create())
        .build();
  }
}
