package com.dvlprmatheus.oauth.api.response;

import com.dvlprmatheus.oauth.entity.enums.SsoProviderType;
import java.util.UUID;

public record LinkStateResponse(UUID userId, SsoProviderType provider) {}
