package com.dvlprmatheus.oauth.api.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.dvlprmatheus.oauth.entity.Role;
import com.dvlprmatheus.oauth.entity.User;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserResponseTest {

  @Test
  void shouldMapUserWithRoleNames() {
    UUID id = UUID.randomUUID();
    LocalDateTime createdAt = LocalDateTime.of(2026, 9, 29, 10, 0);
    Role admin = new Role();
    admin.setName("ADMIN");
    Role user = new Role();
    user.setName("USER");
    User entity =
        User.builder()
            .username("joao")
            .email("joao@example.com")
            .cognitoSub("cognito-sub")
            .roles(Set.of(admin, user))
            .build();
    entity.setId(id);
    entity.setCreatedAt(createdAt);

    UserResponse response = UserResponse.from(entity);

    assertThat(response.id()).isEqualTo(id);
    assertThat(response.username()).isEqualTo("joao");
    assertThat(response.email()).isEqualTo("joao@example.com");
    assertThat(response.roles()).containsExactlyInAnyOrder("ADMIN", "USER");
    assertThat(response.createdAt()).isEqualTo(createdAt);
    assertThat(response.updatedAt()).isNull();
  }

  @Test
  void shouldNotExposeCognitoSub() {
    assertThat(UserResponse.class.getRecordComponents())
        .extracting(component -> component.getName())
        .doesNotContain("cognitoSub");
  }
}
