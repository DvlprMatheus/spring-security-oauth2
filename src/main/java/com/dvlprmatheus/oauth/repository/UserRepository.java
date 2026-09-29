package com.dvlprmatheus.oauth.repository;

import com.dvlprmatheus.oauth.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
  Optional<User> findByUsername(String username);

  Optional<User> findByEmail(String email);

  Optional<User> findByCognitoSub(String cognitoSub);

  Boolean existsByUsername(String username);

  Boolean existsByEmail(String email);
}
