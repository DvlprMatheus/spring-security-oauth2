package com.dvlprmatheus.oauth.api.request;

import com.dvlprmatheus.oauth.entity.enums.SsoProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSsoProviderRequest(
    @NotNull(message = "Provider is required") SsoProviderType type,
    @NotBlank(message = "Identity provider is required")
        @Size(max = 128, message = "Identity provider must be at most 128 characters")
        String identityProvider) {}
