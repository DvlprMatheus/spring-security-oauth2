package com.dvlprmatheus.oauth.api.controller;

import com.dvlprmatheus.oauth.api.request.AuthRequest;
import com.dvlprmatheus.oauth.api.request.ConfirmEmailRequest;
import com.dvlprmatheus.oauth.api.request.RefreshRequest;
import com.dvlprmatheus.oauth.api.request.RegisterRequest;
import com.dvlprmatheus.oauth.api.request.ResendCodeRequest;
import com.dvlprmatheus.oauth.api.response.AuthResponse;
import com.dvlprmatheus.oauth.config.security.CognitoAuthenticationToken;
import com.dvlprmatheus.oauth.service.AuthenticationService;
import com.dvlprmatheus.oauth.util.LogSanitizer;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/auth")
public class AuthController {

  private final AuthenticationService authenticationService;

  @PostMapping("/register")
  public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
    log.info(
        "POST /auth/register - Registering user with username: {}",
        LogSanitizer.sanitize(request.username()));
    authenticationService.register(request);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/confirm-email")
  public ResponseEntity<Void> confirmEmail(@Valid @RequestBody ConfirmEmailRequest request) {
    log.info(
        "POST /auth/confirm-email - Confirming email: {}", LogSanitizer.sanitize(request.email()));
    authenticationService.confirmEmail(request);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/resend-confirmation-code")
  public ResponseEntity<Void> resendConfirmationCode(
      @Valid @RequestBody ResendCodeRequest request) {
    log.info(
        "POST /auth/resend-confirmation-code - Resending confirmation code to email: {}",
        LogSanitizer.sanitize(request.email()));
    authenticationService.resendConfirmationCode(request);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
    log.info(
        "POST /auth/login - Authenticating user with email: {}",
        LogSanitizer.sanitize(request.email()));
    return ResponseEntity.ok(authenticationService.authenticate(request));
  }

  @PostMapping("/refresh")
  public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
    log.info(
        "POST /auth/refresh - Refreshing tokens for user with email: {}",
        LogSanitizer.sanitize(request.email()));
    return ResponseEntity.ok(authenticationService.refresh(request));
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(CognitoAuthenticationToken authentication) {
    log.info("POST /auth/logout - Logging out user");
    authenticationService.logout(authentication.getToken());
    return ResponseEntity.noContent().build();
  }
}
