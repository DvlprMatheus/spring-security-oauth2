package com.dvlprmatheus.oauth.api.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotEmpty(message = "Username is required")
        @Size(min = 3, max = 32, message = "Username must be between 3 and 32 characters")
        String username,
    @NotEmpty(message = "Email is required")
        @Email(message = "Invalid email")
        @Size(max = 255, message = "Email must be less than 255 characters")
        String email,
    @NotEmpty(message = "Password is required")
        @Size(min = 8, max = 32, message = "Password must be between 8 and 32 characters")
        String password) {}
