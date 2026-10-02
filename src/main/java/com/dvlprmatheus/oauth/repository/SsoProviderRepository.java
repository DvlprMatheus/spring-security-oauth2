package com.dvlprmatheus.oauth.repository;

import com.dvlprmatheus.oauth.entity.SsoProvider;
import com.dvlprmatheus.oauth.entity.enums.SsoProviderType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SsoProviderRepository extends JpaRepository<SsoProvider, UUID> {
  Optional<SsoProvider> findByType(SsoProviderType type);
}
