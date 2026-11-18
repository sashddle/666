package com.example.rest1.pip;

public record LoginReq(
        String email,
        String password,
        String deviceId
) {
}