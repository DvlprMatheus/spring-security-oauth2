package com.dvlprmatheus.oauth.api.controller;

import com.dvlprmatheus.oauth.api.request.CreateSsoProviderRequest;
import com.dvlprmatheus.oauth.entity.enums.SsoProviderType;
import com.dvlprmatheus.oauth.service.SsoProviderService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/v1/sso-providers")
public class SsoProviderController {

  private final SsoProviderService ssoProviderService;

  @PostMapping
  public ResponseEntity<Void> create(@Valid @RequestBody CreateSsoProviderRequest request) {
    log.info("POST /v1/sso-providers - Registering {}", request.type());
    ssoProviderService.create(request.type(), request.identityProvider());
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{type}")
  public ResponseEntity<Void> delete(@PathVariable SsoProviderType type) {
    log.info("DELETE /v1/sso-providers/{} - Removing identity provider", type);
    ssoProviderService.delete(type);
    return ResponseEntity.noContent().build();
  }
}
