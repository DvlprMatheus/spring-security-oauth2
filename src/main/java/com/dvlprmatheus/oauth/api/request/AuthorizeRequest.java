package com.dvlprmatheus.oauth.api.request;

import com.dvlprmatheus.oauth.entity.enums.SsoProviderType;
import jakarta.validation.constraints.NotNull;

public record AuthorizeRequest(
    @NotNull(message = "Provider is required") SsoProviderType provider) {}
