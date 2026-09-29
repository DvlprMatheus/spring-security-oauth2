package com.dvlprmatheus.oauth.service.aws.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CognitoUserInfo(String sub, String email) {}
