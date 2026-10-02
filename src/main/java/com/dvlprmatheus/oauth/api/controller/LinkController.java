package com.dvlprmatheus.oauth.api.controller;

import com.dvlprmatheus.oauth.api.request.CreateLocalLoginRequest;
import com.dvlprmatheus.oauth.api.response.AuthorizationUrlResponse;
import com.dvlprmatheus.oauth.config.security.CognitoAuthenticationToken;
import com.dvlprmatheus.oauth.entity.enums.SsoProviderType;
import com.dvlprmatheus.oauth.service.LinkService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/v1/link")
public class LinkController {

  private final LinkService linkService;

  @PostMapping("/local")
  public ResponseEntity<Void> createLocalLogin(
      CognitoAuthenticationToken authentication,
      @Valid @RequestBody CreateLocalLoginRequest request) {
    log.info("POST /v1/link/local - Creating local login linked to identity provider");
    linkService.createLocalLogin(
        authentication.getUser(), authentication.getToken().getTokenValue(), request.password());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{provider}")
  public ResponseEntity<AuthorizationUrlResponse> linkProvider(
      CognitoAuthenticationToken authentication, @PathVariable SsoProviderType provider) {
    log.info("GET /v1/link/{} - Starting identity provider link", provider);
    return ResponseEntity.ok(
        new AuthorizationUrlResponse(
            linkService.federatedLinkUrl(authentication.getUser(), provider)));
  }
}
