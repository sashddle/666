package com.example.rest1.pip;

public record TokenResponse(
        String accessToken,
        String refreshToken
) {
}