package com.dvlprmatheus.oauth.api.controller;

import com.dvlprmatheus.oauth.api.response.UserResponse;
import com.dvlprmatheus.oauth.service.UserService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequestMapping("/v1/users")
@AllArgsConstructor
@RestController
public class UserController {

  private final UserService userService;

  @GetMapping("/current")
  public ResponseEntity<UserResponse> findCurrentUser() {
    log.info("GET /v1/users/current - Finding current user");
    return ResponseEntity.ok(UserResponse.from(userService.findCurrentUser()));
  }
}
