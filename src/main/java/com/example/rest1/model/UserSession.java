package com.example.rest1.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_sessions")
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(nullable = false)
    public String username;

    @Column(name = "device_id")
    public String deviceId;

    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    public String accessToken;

    @Column(name = "refresh_token", nullable = false, unique = true, columnDefinition = "TEXT")
    public String refreshToken;

    @Column(name = "access_token_expiry", nullable = false)
    public Instant accessTokenExpiry;

    @Column(name = "refresh_token_expiry", nullable = false)
    public Instant refreshTokenExpiry;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public SessionStatus status;
}