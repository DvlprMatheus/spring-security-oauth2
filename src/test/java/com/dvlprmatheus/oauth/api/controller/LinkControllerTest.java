package com.dvlprmatheus.oauth.api.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dvlprmatheus.oauth.api.exception.handler.GlobalExceptionHandler;
import com.dvlprmatheus.oauth.config.security.CognitoAuthenticationToken;
import com.dvlprmatheus.oauth.entity.User;
import com.dvlprmatheus.oauth.entity.enums.SsoProviderType;
import com.dvlprmatheus.oauth.service.LinkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class LinkControllerTest {

  @Mock private LinkService linkService;
  @InjectMocks private LinkController linkController;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(linkController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void shouldCreateLocalLoginForAuthenticatedUser() throws Exception {
    User user = User.builder().email("joao@example.com").cognitoSub("federated-sub").build();
    Jwt jwt =
        Jwt.withTokenValue("access-token").header("alg", "none").subject("federated-sub").build();
    CognitoAuthenticationToken authentication = new CognitoAuthenticationToken(user, jwt);

    mockMvc
        .perform(
            post("/v1/link/local")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"Str0ngPass!\"}"))
        .andExpect(status().isNoContent());

    verify(linkService).createLocalLogin(user, "access-token", "Str0ngPass!");
  }

  @Test
  void shouldReturnAuthorizationUrlToLinkProvider() throws Exception {
    User user = User.builder().email("joao@example.com").cognitoSub("local-sub").build();
    Jwt jwt = Jwt.withTokenValue("access-token").header("alg", "none").subject("local-sub").build();
    when(linkService.federatedLinkUrl(user, SsoProviderType.GOOGLE))
        .thenReturn("https://authorize");

    mockMvc
        .perform(get("/v1/link/GOOGLE").principal(new CognitoAuthenticationToken(user, jwt)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.authorizationUrl").value("https://authorize"));
  }
}
