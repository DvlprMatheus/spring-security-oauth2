package com.dvlprmatheus.oauth.api.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record CreateLocalLoginRequest(
    @NotEmpty(message = "Password is required")
        @Size(min = 8, max = 32, message = "Password must be between 8 and 32 characters")
        String password) {}
