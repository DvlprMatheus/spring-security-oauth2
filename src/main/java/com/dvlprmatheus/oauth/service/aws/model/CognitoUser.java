package com.dvlprmatheus.oauth.service.aws.model;

import java.util.List;
import java.util.Optional;

public record CognitoUser(
    String username, String sub, String email, List<CognitoIdentity> identities) {

  public CognitoUser {
    identities = identities == null ? List.of() : List.copyOf(identities);
  }

  public Optional<CognitoIdentity> federatedIdentity() {
    return identities.stream()
        .filter(identity -> identity.providerName() != null)
        .filter(identity -> !"Cognito".equalsIgnoreCase(identity.providerName()))
        .findFirst();
  }
}
