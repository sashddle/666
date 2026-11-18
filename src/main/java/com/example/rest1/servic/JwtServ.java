
package com.example.rest1.servic;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtServ {

    private final SecretKey key;
    private final long accessExpiration;
    private final long refreshExpiration;

    public JwtServ(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-expiration}") long accessExpiration,
            @Value("${jwt.refresh-expiration}") long refreshExpiration
    ) {
        this.key = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );

        this.accessExpiration = accessExpiration;
        this.refreshExpiration = refreshExpiration;
    }

    public String createAccess(String username) {

        return createToken(
                username,
                "access",
                accessExpiration
        );
    }

    public String createRefresh(String username) {

        return createToken(
                username,
                "refresh",
                refreshExpiration
        );
    }

    private String createToken(
            String username,
            String type,
            long expiration
    ) {

        Instant now = Instant.now()
                .truncatedTo(ChronoUnit.SECONDS);

        Instant expiry = now.plusSeconds(expiration);

        return Jwts.builder()
                .subject(username)
                .id(UUID.randomUUID().toString())
                .claim("type", type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public Claims validateAccess(String token) {

        return validateToken(token, "access");
    }

    public Claims validateRefresh(String token) {

        return validateToken(token, "refresh");
    }

    private Claims validateToken(
            String token,
            String type
    ) {

        if (token == null || token.isBlank()) {
            throw new JwtException("Token is empty");
        }

        var parsed = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);

        if (!"HS256".equals(parsed.getHeader().getAlgorithm())) {
            throw new JwtException("Invalid algorithm");
        }

        Claims claims = parsed.getPayload();

        if (claims.getSubject() == null
                || claims.getSubject().isBlank()
                || claims.getId() == null
                || claims.getId().isBlank()
                || claims.getIssuedAt() == null
                || claims.getExpiration() == null) {

            throw new JwtException("Invalid token claims");
        }

        if (!type.equals(claims.get("type", String.class))) {
            throw new JwtException("Invalid token type");
        }

        if (!claims.getExpiration().toInstant()
                .isAfter(Instant.now())) {

            throw new JwtException("Token expired");
        }

        return claims;
    }
}
