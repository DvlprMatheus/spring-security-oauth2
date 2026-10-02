package com.dvlprmatheus.oauth.entity;

import com.dvlprmatheus.oauth.entity.enums.SsoProviderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "sso_providers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SsoProvider extends AbstractEntity {

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, unique = true, length = 50)
  private SsoProviderType type;

  @Column(name = "identity_provider", nullable = false, length = 128)
  private String identityProvider;
}
