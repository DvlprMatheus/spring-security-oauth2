package com.dvlprmatheus.oauth.api.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

public record ConfirmEmailRequest(
    @NotEmpty(message = "Email is required") @Email(message = "Invalid email") String email,
    @NotEmpty(message = "Code is required")
        @Pattern(regexp = "\\d{6}", message = "Code must have 6 digits")
        String code) {}
