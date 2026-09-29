package com.dvlprmatheus.oauth.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dvlprmatheus.oauth.entity.Role;
import com.dvlprmatheus.oauth.entity.User;
import com.dvlprmatheus.oauth.service.UserService;
import com.dvlprmatheus.oauth.service.aws.CognitoService;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;

@ExtendWith(MockitoExtension.class)
class CognitoJwtAuthenticationConverterTest {

  private static final String COGNITO_SUB = "cognito-sub-123";
  private static final Jwt JWT =
      Jwt.withTokenValue("access-token").header("alg", "none").subject(COGNITO_SUB).build();

  @Mock private UserService userService;
  @Mock private CognitoService cognitoService;
  @InjectMocks private CognitoJwtAuthenticationConverter converter;

  @Test
  void shouldAuthenticateUserWithRolesWhenTokenIsActive() {
    Role admin = new Role();
    admin.setName("ADMIN");
    User user = User.builder().cognitoSub(COGNITO_SUB).roles(Set.of(admin)).build();
    when(userService.findByCognitoSubOptional(COGNITO_SUB)).thenReturn(Optional.of(user));

    AbstractAuthenticationToken authentication = converter.convert(JWT);

    assertThat(authentication).isInstanceOf(CognitoAuthenticationToken.class);
    assertThat(authentication.isAuthenticated()).isTrue();
    assertThat(authentication.getPrincipal()).isSameAs(user);
    assertThat(authentication.getCredentials()).isSameAs(JWT);
    assertThat(authentication.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_ADMIN");
  }

  @Test
  void shouldRejectTokenRevokedByLogout() {
    doThrow(NotAuthorizedException.builder().message("Access Token has been revoked").build())
        .when(cognitoService)
        .getUser("access-token");

    assertThatThrownBy(() -> converter.convert(JWT))
        .isInstanceOf(InvalidBearerTokenException.class)
        .hasMessage("Token has been revoked");
    verifyNoInteractions(userService);
  }

  @Test
  void shouldFailClosedWhenCognitoIsUnavailable() {
    doThrow(SdkClientException.create("Unable to connect"))
        .when(cognitoService)
        .getUser("access-token");

    assertThatThrownBy(() -> converter.convert(JWT))
        .isInstanceOf(AuthenticationServiceException.class);
    verifyNoInteractions(userService);
  }

  @Test
  void shouldRejectTokenOfUserNotInDatabase() {
    when(userService.findByCognitoSubOptional(COGNITO_SUB)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> converter.convert(JWT))
        .isInstanceOf(UsernameNotFoundException.class)
        .hasMessage("User not found");
  }
}
