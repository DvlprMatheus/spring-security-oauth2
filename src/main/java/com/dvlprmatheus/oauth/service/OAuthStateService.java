package com.dvlprmatheus.oauth.service;

import com.dvlprmatheus.oauth.api.exception.AuthenticationFailedException;
import com.dvlprmatheus.oauth.api.response.LinkStateResponse;
import com.dvlprmatheus.oauth.config.properties.CognitoProperties;
import com.dvlprmatheus.oauth.entity.enums.SsoProviderType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class OAuthStateService {

  private static final Duration TTL = Duration.ofMinutes(10);
  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

  private final CognitoProperties cognitoProperties;
  private final Clock clock;

  @Autowired
  public OAuthStateService(CognitoProperties cognitoProperties) {
    this(cognitoProperties, Clock.systemUTC());
  }

  OAuthStateService(CognitoProperties cognitoProperties, Clock clock) {
    this.cognitoProperties = cognitoProperties;
    this.clock = clock;
  }

  public String create(UUID userId, SsoProviderType provider) {
    String payload =
        String.join(
            "|",
            userId.toString(),
            provider.name(),
            String.valueOf(clock.instant().plus(TTL).getEpochSecond()));
    String encodedPayload = ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    return encodedPayload + "." + ENCODER.encodeToString(sign(encodedPayload));
  }

  public LinkStateResponse verify(String state) {
    try {
      String[] parts = state.split("\\.", -1);
      if (parts.length != 2 || !MessageDigest.isEqual(sign(parts[0]), DECODER.decode(parts[1]))) {
        throw new IllegalArgumentException("Invalid signature");
      }
      String[] payload =
          new String(DECODER.decode(parts[0]), StandardCharsets.UTF_8).split("\\|", -1);
      if (payload.length != 3
          || clock.instant().isAfter(Instant.ofEpochSecond(Long.parseLong(payload[2])))) {
        throw new IllegalArgumentException("Expired state");
      }
      return new LinkStateResponse(
          UUID.fromString(payload[0]), SsoProviderType.valueOf(payload[1]));
    } catch (IllegalStateException e) {
      throw e;
    } catch (RuntimeException e) {
      log.warn("Invalid or expired link state");
      throw new AuthenticationFailedException("Invalid or expired link request", e);
    }
  }

  private byte[] sign(String value) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(
          new SecretKeySpec(
              cognitoProperties.clientSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new IllegalStateException("Error signing link state", e);
    }
  }
}
