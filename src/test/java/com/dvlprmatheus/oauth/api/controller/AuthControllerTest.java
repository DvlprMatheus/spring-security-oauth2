package com.dvlprmatheus.oauth.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dvlprmatheus.oauth.api.exception.AuthenticationFailedException;
import com.dvlprmatheus.oauth.api.exception.handler.GlobalExceptionHandler;
import com.dvlprmatheus.oauth.api.request.AuthRequest;
import com.dvlprmatheus.oauth.api.request.ConfirmEmailRequest;
import com.dvlprmatheus.oauth.api.request.RegisterRequest;
import com.dvlprmatheus.oauth.api.request.ResendCodeRequest;
import com.dvlprmatheus.oauth.api.response.AuthResponse;
import com.dvlprmatheus.oauth.config.security.CognitoAuthenticationToken;
import com.dvlprmatheus.oauth.entity.User;
import com.dvlprmatheus.oauth.service.AuthenticationService;
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
class AuthControllerTest {

  @Mock private AuthenticationService authenticationService;
  @InjectMocks private AuthController authController;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(authController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void shouldRegisterUser() throws Exception {
    mockMvc
        .perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"username":"joao","email":"joao@example.com","password":"Str0ngPass!"}
                    """))
        .andExpect(status().isNoContent());

    verify(authenticationService)
        .register(new RegisterRequest("joao", "joao@example.com", "Str0ngPass!"));
  }

  @Test
  void shouldRejectInvalidRegisterRequest() throws Exception {
    mockMvc
        .perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"username":"joao","email":"not-an-email","password":"Str0ngPass!"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.message").value("Invalid email"));

    verifyNoInteractions(authenticationService);
  }

  @Test
  void shouldConfirmEmail() throws Exception {
    mockMvc
        .perform(
            post("/auth/confirm-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"joao@example.com\",\"code\":\"123456\"}"))
        .andExpect(status().isNoContent());

    verify(authenticationService)
        .confirmEmail(new ConfirmEmailRequest("joao@example.com", "123456"));
  }

  @Test
  void shouldRejectConfirmationCodeWithoutSixDigits() throws Exception {
    mockMvc
        .perform(
            post("/auth/confirm-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"joao@example.com\",\"code\":\"12ab\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Code must have 6 digits"));

    verifyNoInteractions(authenticationService);
  }

  @Test
  void shouldResendConfirmationCode() throws Exception {
    mockMvc
        .perform(
            post("/auth/resend-confirmation-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"joao@example.com\"}"))
        .andExpect(status().isNoContent());

    verify(authenticationService).resendConfirmationCode(new ResendCodeRequest("joao@example.com"));
  }

  @Test
  void shouldReturnTokensOnLogin() throws Exception {
    when(authenticationService.authenticate(new AuthRequest("joao@example.com", "Str0ngPass!")))
        .thenReturn(new AuthResponse("access-token", "id-token", "refresh-token", "Bearer", 3600));

    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"joao@example.com\",\"password\":\"Str0ngPass!\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("access-token"))
        .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
        .andExpect(jsonPath("$.expiresIn").value(3600));
  }

  @Test
  void shouldReturnUnauthorizedWhenLoginFails() throws Exception {
    when(authenticationService.authenticate(any()))
        .thenThrow(new AuthenticationFailedException("Invalid email or password"));

    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"joao@example.com\",\"password\":\"wrong\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Invalid email or password"));
  }

  @Test
  void shouldRefreshTokens() throws Exception {
    when(authenticationService.refresh(any()))
        .thenReturn(new AuthResponse("new-access", "new-id", "refresh-token", "Bearer", 3600));

    mockMvc
        .perform(
            post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"joao@example.com\",\"refreshToken\":\"refresh-token\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("new-access"));
  }

  @Test
  void shouldLogoutAuthenticatedUser() throws Exception {
    Jwt jwt = Jwt.withTokenValue("access-token").header("alg", "none").subject("sub").build();
    CognitoAuthenticationToken authentication =
        new CognitoAuthenticationToken(User.builder().cognitoSub("sub").build(), jwt);

    mockMvc
        .perform(post("/auth/logout").principal(authentication))
        .andExpect(status().isNoContent());

    verify(authenticationService).logout(jwt);
  }
}
