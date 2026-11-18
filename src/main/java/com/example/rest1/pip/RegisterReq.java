package com.example.rest1.pip;

public record RegisterReq(
        String name,
        String email,
        String password,
        String deviceId
) {
}