package com.dvlprmatheus.oauth.api.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;

public record AuthRequest(
    @NotEmpty(message = "Email is required") @Email(message = "Invalid email") String email,
    @NotEmpty(message = "Password is required") String password) {}
