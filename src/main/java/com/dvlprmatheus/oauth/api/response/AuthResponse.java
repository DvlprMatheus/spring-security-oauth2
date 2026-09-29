package com.dvlprmatheus.oauth.api.response;

public record AuthResponse(
    String accessToken, String idToken, String refreshToken, String tokenType, Integer expiresIn) {}
