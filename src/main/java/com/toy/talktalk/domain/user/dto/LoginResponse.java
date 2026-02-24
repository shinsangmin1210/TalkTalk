package com.toy.talktalk.domain.user.dto;

public record LoginResponse(String accessToken, String tokenType) {

    public static LoginResponse of(String accessToken) {
        return new LoginResponse(accessToken, "Bearer");
    }
}
