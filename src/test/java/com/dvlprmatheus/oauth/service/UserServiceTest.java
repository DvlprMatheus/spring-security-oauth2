package com.dvlprmatheus.oauth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dvlprmatheus.oauth.api.exception.UserNotExistsException;
import com.dvlprmatheus.oauth.config.security.CognitoAuthenticationToken;
import com.dvlprmatheus.oauth.entity.User;
import com.dvlprmatheus.oauth.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  private static final String EMAIL = "joao@example.com";

  @Mock private UserRepository userRepository;
  @InjectMocks private UserService userService;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldReturnAuthenticatedUserAsCurrentUser() {
    User user = User.builder().email(EMAIL).cognitoSub("sub").build();
    Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("sub").build();
    SecurityContextHolder.getContext().setAuthentication(new CognitoAuthenticationToken(user, jwt));

    assertThat(userService.findCurrentUser()).isSameAs(user);
  }

  @Test
  void shouldFailWhenThereIsNoAuthenticatedUser() {
    assertThatThrownBy(() -> userService.findCurrentUser())
        .isInstanceOf(UserNotExistsException.class)
        .hasMessage("User not found");
  }

  @Test
  void shouldFindUserByEmail() {
    User user = User.builder().email(EMAIL).build();
    when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));

    assertThat(userService.findByEmail(EMAIL)).isSameAs(user);
  }

  @Test
  void shouldFailWhenEmailIsNotFound() {
    when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.findByEmail(EMAIL))
        .isInstanceOf(UserNotExistsException.class)
        .hasMessage("User not found");
  }

  @Test
  void shouldDelegateExistenceChecksToRepository() {
    when(userRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(true);
    when(userRepository.existsByUsername("joao")).thenReturn(false);

    assertThat(userService.existsByEmail(EMAIL)).isTrue();
    assertThat(userService.existsByUsername("joao")).isFalse();
  }

  @Test
  void shouldFindUserById() {
    UUID id = UUID.fromString("7b1e4c1a-3f2d-4a8e-9c5b-2d6f8e0a1b2c");
    User user = User.builder().email(EMAIL).build();
    when(userRepository.findById(id)).thenReturn(Optional.of(user));

    assertThat(userService.findById(id)).isSameAs(user);
  }

  @Test
  void shouldFindUserByCognitoSub() {
    User user = User.builder().cognitoSub("sub").build();
    when(userRepository.findByCognitoSub("sub")).thenReturn(Optional.of(user));

    assertThat(userService.findByCognitoSubOptional("sub")).containsSame(user);
  }

  @Test
  void shouldSaveUser() {
    User user = User.builder().email(EMAIL).build();
    when(userRepository.save(user)).thenReturn(user);

    assertThat(userService.save(user)).isSameAs(user);
    verify(userRepository).save(user);
  }
}
