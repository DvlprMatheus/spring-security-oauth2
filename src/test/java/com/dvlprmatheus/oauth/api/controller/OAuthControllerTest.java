package com.dvlprmatheus.oauth.api.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dvlprmatheus.oauth.api.exception.AuthenticationFailedException;
import com.dvlprmatheus.oauth.api.exception.handler.GlobalExceptionHandler;
import com.dvlprmatheus.oauth.api.response.AuthResponse;
import com.dvlprmatheus.oauth.entity.enums.SsoProviderType;
import com.dvlprmatheus.oauth.service.LinkService;
import com.dvlprmatheus.oauth.service.OAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class OAuthControllerTest {

  @Mock private OAuthService oAuthService;
  @Mock private LinkService linkService;
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
  void shouldReturnAuthorizationUrl() throws Exception {
    when(oAuthService.authorizeUrl(SsoProviderType.MICROSOFT))
        .thenReturn("https://cognito.example.com/oauth2/authorize?identity_provider=Microsoft");

    mockMvc
        .perform(
            post("/oauth2/authorize")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"MICROSOFT\"}"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.authorizationUrl")
                .value("https://cognito.example.com/oauth2/authorize?identity_provider=Microsoft"));
  }

  @Test
  void shouldRejectAuthorizeRequestWithoutProvider() throws Exception {
    mockMvc
        .perform(post("/oauth2/authorize").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Provider is required"));

    verifyNoInteractions(oAuthService, linkService);
  }

  @Test
  void shouldReturnTokensOnCallback() throws Exception {
    when(oAuthService.authenticate("authorization-code", null, null))
        .thenReturn(new AuthResponse("access-token", "id-token", "refresh-token", "Bearer", 3600));

    mockMvc
        .perform(get("/oauth2/callback").param("code", "authorization-code"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("access-token"))
        .andExpect(jsonPath("$.tokenType").value("Bearer"));

    verifyNoInteractions(linkService);
  }

  @Test
  void shouldLinkIdentityWhenCallbackReceivesState() throws Exception {
    mockMvc
        .perform(
            get("/oauth2/callback").param("code", "authorization-code").param("state", "signed"))
        .andExpect(status().isNoContent());

    verify(linkService).linkFederatedIdentity("authorization-code", "signed", null, null);
    verifyNoInteractions(oAuthService);
  }

  @Test
  void shouldForwardProviderErrorToService() throws Exception {
    when(oAuthService.authenticate(null, "invalid_request", "invalid_scope"))
        .thenThrow(new AuthenticationFailedException("Identity provider authentication failed"));

    mockMvc
        .perform(
            get("/oauth2/callback")
                .param("error", "invalid_request")
                .param("error_description", "invalid_scope"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Identity provider authentication failed"));
  }
}
