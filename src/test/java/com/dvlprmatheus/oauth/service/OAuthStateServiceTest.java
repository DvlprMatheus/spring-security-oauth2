package com.dvlprmatheus.oauth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dvlprmatheus.oauth.api.exception.AuthenticationFailedException;
import com.dvlprmatheus.oauth.api.response.LinkStateResponse;
import com.dvlprmatheus.oauth.config.properties.CognitoProperties;
import com.dvlprmatheus.oauth.entity.enums.SsoProviderType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OAuthStateServiceTest {

  private static final UUID USER_ID = UUID.fromString("7b1e4c1a-3f2d-4a8e-9c5b-2d6f8e0a1b2c");
  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private final CognitoProperties properties =
      new CognitoProperties(
          "sa-east-1",
          "pool-id",
          "client-id",
          "client-secret",
          "https://cognito.example.com",
          "http://localhost:8080/oauth2/callback");

  @Test
  void shouldRoundTripSignedState() {
    OAuthStateService service = serviceAt(NOW);

    String state = service.create(USER_ID, SsoProviderType.MICROSOFT);

    assertThat(service.verify(state))
        .isEqualTo(new LinkStateResponse(USER_ID, SsoProviderType.MICROSOFT));
    assertThat(state).doesNotContain(USER_ID.toString());
  }

  @Test
  void shouldRejectTamperedState() {
    OAuthStateService service = serviceAt(NOW);
    String state = service.create(USER_ID, SsoProviderType.GOOGLE);
    String tampered = state.substring(1);

    assertThatThrownBy(() -> service.verify(tampered))
        .isInstanceOf(AuthenticationFailedException.class)
        .hasMessage("Invalid or expired link request");
  }

  @Test
  void shouldRejectExpiredState() {
    String state = serviceAt(NOW).create(USER_ID, SsoProviderType.APPLE);
    OAuthStateService later = serviceAt(NOW.plusSeconds(601));

    assertThatThrownBy(() -> later.verify(state))
        .isInstanceOf(AuthenticationFailedException.class)
        .hasMessage("Invalid or expired link request");
  }

  private OAuthStateService serviceAt(Instant instant) {
    return new OAuthStateService(properties, Clock.fixed(instant, ZoneOffset.UTC));
  }
}
