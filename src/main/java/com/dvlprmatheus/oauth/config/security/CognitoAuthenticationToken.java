package com.dvlprmatheus.oauth.config.security;

import com.dvlprmatheus.oauth.entity.User;
import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

@Getter
public class CognitoAuthenticationToken extends AbstractAuthenticationToken {

  private final User user;
  private final Jwt token;

  public CognitoAuthenticationToken(User user, Jwt token) {
    super(user.getAuthorities());
    this.user = user;
    this.token = token;
    setAuthenticated(true);
  }

  @Override
  public Object getPrincipal() {
    return user;
  }

  @Override
  public Object getCredentials() {
    return token;
  }
}
