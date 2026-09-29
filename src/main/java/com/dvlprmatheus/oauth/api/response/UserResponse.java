package com.dvlprmatheus.oauth.api.response;

import com.dvlprmatheus.oauth.entity.Role;
import com.dvlprmatheus.oauth.entity.User;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record UserResponse(
    UUID id,
    String username,
    String email,
    Set<String> roles,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static UserResponse from(User user) {
    return new UserResponse(
        user.getId(),
        user.getUsername(),
        user.getEmail(),
        user.getRoles().stream().map(Role::getName).collect(Collectors.toSet()),
        user.getCreatedAt(),
        user.getUpdatedAt());
  }
}
