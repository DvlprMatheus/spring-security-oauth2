package com.dvlprmatheus.oauth.service;

import com.dvlprmatheus.oauth.api.exception.UserNotExistsException;
import com.dvlprmatheus.oauth.entity.User;
import com.dvlprmatheus.oauth.repository.UserRepository;
import com.dvlprmatheus.oauth.util.LogSanitizer;
import java.util.Optional;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@AllArgsConstructor
@Service
public class UserService {

  private final UserRepository userRepository;

  public User findCurrentUser() {
    log.info("Attempting to find current user");
    return User.currentUser()
        .orElseThrow(
            () -> {
              log.warn("No current user found");
              throw new UserNotExistsException("User not found");
            });
  }

  public boolean existsByUsername(String username) {
    log.info("Checking if username {} exists", LogSanitizer.sanitize(username));
    return userRepository.existsByUsername(username);
  }

  public boolean existsByEmail(String email) {
    log.info("Checking if email {} exists", LogSanitizer.sanitize(email));
    return userRepository.existsByEmailIgnoreCase(email);
  }

  public Optional<User> findByEmailOptional(String email) {
    return userRepository.findByEmailIgnoreCase(email);
  }

  public User findByEmail(String email) {
    return findByEmailOptional(email)
        .orElseThrow(
            () -> {
              log.warn("User with email {} not found in database", LogSanitizer.sanitize(email));
              return new UserNotExistsException("User not found");
            });
  }

  public User findById(UUID id) {
    return userRepository
        .findById(id)
        .orElseThrow(
            () -> {
              log.warn(
                  "User with id {} not found in database", LogSanitizer.sanitize(id.toString()));
              return new UserNotExistsException("User not found");
            });
  }

  public Optional<User> findByCognitoSubOptional(String cognitoSub) {
    return userRepository.findByCognitoSub(cognitoSub);
  }

  public User save(User user) {
    return userRepository.save(user);
  }
}
