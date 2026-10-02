package com.dvlprmatheus.oauth.api.controller;

import com.dvlprmatheus.oauth.api.request.AuthorizeRequest;
import com.dvlprmatheus.oauth.api.response.AuthorizationUrlResponse;
import com.dvlprmatheus.oauth.service.LinkService;
import com.dvlprmatheus.oauth.service.OAuthService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/oauth2")
public class OAuthController {

  private final OAuthService oAuthService;
  private final LinkService linkService;

  @PostMapping("/authorize")
  public ResponseEntity<AuthorizationUrlResponse> authorize(
      @Valid @RequestBody AuthorizeRequest request) {
    log.info("POST /oauth2/authorize - Starting {} login", request.provider());
    return ResponseEntity.ok(
        new AuthorizationUrlResponse(oAuthService.authorizeUrl(request.provider())));
  }

  @GetMapping("/callback")
  public ResponseEntity<?> callback(
      @RequestParam(required = false) String code,
      @RequestParam(required = false) String state,
      @RequestParam(required = false) String error,
      @RequestParam(name = "error_description", required = false) String errorDescription) {
    if (state != null) {
      log.info("GET /oauth2/callback - Linking identity provider");
      linkService.linkFederatedIdentity(code, state, error, errorDescription);
      return ResponseEntity.noContent().build();
    }
    log.info("GET /oauth2/callback - Authenticating user with identity provider");
    return ResponseEntity.ok(oAuthService.authenticate(code, error, errorDescription));
  }
}
