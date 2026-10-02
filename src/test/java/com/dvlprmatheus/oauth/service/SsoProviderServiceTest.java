package com.dvlprmatheus.oauth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dvlprmatheus.oauth.api.exception.SsoProviderNotFoundException;
import com.dvlprmatheus.oauth.api.exception.UserAlreadyExistsException;
import com.dvlprmatheus.oauth.entity.SsoProvider;
import com.dvlprmatheus.oauth.entity.enums.SsoProviderType;
import com.dvlprmatheus.oauth.repository.SsoProviderRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SsoProviderServiceTest {

  @Mock private SsoProviderRepository ssoProviderRepository;
  @InjectMocks private SsoProviderService ssoProviderService;

  @Test
  void shouldRegisterProvider() {
    when(ssoProviderRepository.findByType(SsoProviderType.MICROSOFT)).thenReturn(Optional.empty());

    ssoProviderService.create(SsoProviderType.MICROSOFT, " Microsoft ");

    ArgumentCaptor<SsoProvider> captor = ArgumentCaptor.forClass(SsoProvider.class);
    verify(ssoProviderRepository).save(captor.capture());
    assertThat(captor.getValue().getType()).isEqualTo(SsoProviderType.MICROSOFT);
    assertThat(captor.getValue().getIdentityProvider()).isEqualTo("Microsoft");
  }

  @Test
  void shouldRejectProviderAlreadyRegistered() {
    when(ssoProviderRepository.findByType(SsoProviderType.GOOGLE))
        .thenReturn(Optional.of(SsoProvider.builder().type(SsoProviderType.GOOGLE).build()));

    assertThatThrownBy(() -> ssoProviderService.create(SsoProviderType.GOOGLE, "Google"))
        .isInstanceOf(UserAlreadyExistsException.class)
        .hasMessage("Identity provider already registered");
    verify(ssoProviderRepository, never()).save(any());
  }

  @Test
  void shouldRemoveRegisteredProvider() {
    SsoProvider provider =
        SsoProvider.builder()
            .type(SsoProviderType.APPLE)
            .identityProvider("SignInWithApple")
            .build();
    when(ssoProviderRepository.findByType(SsoProviderType.APPLE)).thenReturn(Optional.of(provider));

    ssoProviderService.delete(SsoProviderType.APPLE);

    verify(ssoProviderRepository).delete(provider);
  }

  @Test
  void shouldReturnRegisteredProvider() {
    SsoProvider provider =
        SsoProvider.builder()
            .type(SsoProviderType.AMAZON)
            .identityProvider("LoginWithAmazon")
            .build();
    when(ssoProviderRepository.findByType(SsoProviderType.AMAZON))
        .thenReturn(Optional.of(provider));

    assertThat(ssoProviderService.findByType(SsoProviderType.AMAZON)).isSameAs(provider);
  }

  @Test
  void shouldFailWhenProviderIsNotRegistered() {
    when(ssoProviderRepository.findByType(SsoProviderType.FACEBOOK)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> ssoProviderService.findByType(SsoProviderType.FACEBOOK))
        .isInstanceOf(SsoProviderNotFoundException.class)
        .hasMessage("Identity provider is not registered");
  }
}
