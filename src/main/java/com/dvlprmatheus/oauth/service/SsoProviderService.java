package com.dvlprmatheus.oauth.service;

import com.dvlprmatheus.oauth.api.exception.SsoProviderNotFoundException;
import com.dvlprmatheus.oauth.api.exception.UserAlreadyExistsException;
import com.dvlprmatheus.oauth.entity.SsoProvider;
import com.dvlprmatheus.oauth.entity.enums.SsoProviderType;
import com.dvlprmatheus.oauth.repository.SsoProviderRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@AllArgsConstructor
@Service
public class SsoProviderService {

  private final SsoProviderRepository ssoProviderRepository;

  public void create(SsoProviderType type, String identityProvider) {
    if (ssoProviderRepository.findByType(type).isPresent()) {
      log.warn("SSO provider {} is already registered", type);
      throw new UserAlreadyExistsException("Identity provider already registered");
    }
    ssoProviderRepository.save(
        SsoProvider.builder().type(type).identityProvider(identityProvider.trim()).build());
    log.info("SSO provider {} registered", type);
  }

  public void delete(SsoProviderType type) {
    SsoProvider provider = findByType(type);
    ssoProviderRepository.delete(provider);
    log.info("SSO provider {} removed", type);
  }

  public SsoProvider findByType(SsoProviderType type) {
    return ssoProviderRepository
        .findByType(type)
        .orElseThrow(
            () -> {
              log.warn("SSO provider {} is not registered", type);
              return new SsoProviderNotFoundException("Identity provider is not registered");
            });
  }
}
