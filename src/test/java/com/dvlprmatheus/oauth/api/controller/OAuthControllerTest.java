package com.dvlprmatheus.oauth.api.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dvlprmatheus.oauth.api.exception.AuthenticationFailedException;
import com.dvlprmatheus.oauth.api.exception.handler.GlobalExceptionHandler;
import com.dvlprmatheus.oauth.api.response.AuthResponse;
import com.dvlprmatheus.oauth.service.OAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class OAuthControllerTest {

  @Mock private OAuthService oAuthService;
  @InjectMocks private OAuthController oAuthController;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(oAuthController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void shouldRedirectToMicrosoftLogin() throws Exception {
    when(oAuthService.microsoftAuthorizeUrl())
        .thenReturn("https://cognito.example.com/oauth2/authorize?identity_provider=Microsoft");

    mockMvc
        .perform(get("/oauth2/microsoft"))
        .andExpect(status().isFound())
        .andExpect(
            header()
                .string(
                    HttpHeaders.LOCATION,
                    "https://cognito.example.com/oauth2/authorize?identity_provider=Microsoft"));
  }

  @Test
  void shouldReturnTokensOnCallback() throws Exception {
    when(oAuthService.authenticateWithMicrosoft("authorization-code", null, null))
        .thenReturn(new AuthResponse("access-token", "id-token", "refresh-token", "Bearer", 3600));

    mockMvc
        .perform(get("/oauth2/microsoft/callback").param("code", "authorization-code"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("access-token"))
        .andExpect(jsonPath("$.tokenType").value("Bearer"));
  }

  @Test
  void shouldForwardProviderErrorToService() throws Exception {
    when(oAuthService.authenticateWithMicrosoft(null, "invalid_request", "invalid_scope"))
        .thenThrow(new AuthenticationFailedException("Microsoft authentication failed"));

    mockMvc
        .perform(
            get("/oauth2/microsoft/callback")
                .param("error", "invalid_request")
                .param("error_description", "invalid_scope"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Microsoft authentication failed"));
  }
}
