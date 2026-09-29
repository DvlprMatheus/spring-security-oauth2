package com.dvlprmatheus.oauth.api.controller;

import com.dvlprmatheus.oauth.api.response.AuthResponse;
import com.dvlprmatheus.oauth.service.OAuthService;
import java.net.URI;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/oauth2")
public class OAuthController {

  private final OAuthService oAuthService;

  @GetMapping("/microsoft")
  public ResponseEntity<Void> microsoftLogin() {
    log.info("GET /oauth2/microsoft - Redirecting to Microsoft login");
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(oAuthService.microsoftAuthorizeUrl()))
        .build();
  }

  @GetMapping("/microsoft/callback")
  public ResponseEntity<AuthResponse> microsoftCallback(
      @RequestParam(required = false) String code,
      @RequestParam(required = false) String error,
      @RequestParam(name = "error_description", required = false) String errorDescription) {
    log.info("GET /oauth2/microsoft/callback - Authenticating user with Microsoft");
    return ResponseEntity.ok(oAuthService.authenticateWithMicrosoft(code, error, errorDescription));
  }
}
