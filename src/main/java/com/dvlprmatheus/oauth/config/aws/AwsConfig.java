package com.dvlprmatheus.oauth.config.aws;

import com.dvlprmatheus.oauth.config.properties.CognitoProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.apache5.Apache5HttpClient;
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
        .credentialsProvider(DefaultCredentialsProvider.builder().build())
        .httpClient(Apache5HttpClient.create())
        .build();
  }
}
