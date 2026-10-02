package com.dvlprmatheus.oauth.api.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dvlprmatheus.oauth.api.exception.handler.GlobalExceptionHandler;
import com.dvlprmatheus.oauth.entity.enums.SsoProviderType;
import com.dvlprmatheus.oauth.service.SsoProviderService;
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
class SsoProviderControllerTest {

  @Mock private SsoProviderService ssoProviderService;
  @InjectMocks private SsoProviderController ssoProviderController;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(ssoProviderController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void shouldRegisterProvider() throws Exception {
    mockMvc
        .perform(
            post("/v1/sso-providers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"MICROSOFT\",\"identityProvider\":\"Microsoft\"}"))
        .andExpect(status().isNoContent());

    verify(ssoProviderService).create(SsoProviderType.MICROSOFT, "Microsoft");
  }

  @Test
  void shouldRejectProviderWithoutIdentityName() throws Exception {
    mockMvc
        .perform(
            post("/v1/sso-providers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"GOOGLE\",\"identityProvider\":\" \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Identity provider is required"));

    verifyNoInteractions(ssoProviderService);
  }

  @Test
  void shouldRemoveProvider() throws Exception {
    mockMvc.perform(delete("/v1/sso-providers/APPLE")).andExpect(status().isNoContent());

    verify(ssoProviderService).delete(SsoProviderType.APPLE);
  }
}
