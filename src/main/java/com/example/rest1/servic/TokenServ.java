
package com.example.rest1.servic;

import com.example.rest1.model.*;
import com.example.rest1.pip.TokenResponse;
import com.example.rest1.repo.UserRepo;
import com.example.rest1.repo.UserSessionRepo;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

@Service
public class TokenServ {

    private final JwtServ jwt;
    private final UserSessionRepo sessions;
    private final UserRepo users;

    public TokenServ(
            JwtServ jwt,
            UserSessionRepo sessions,
            UserRepo users
    ) {
        this.jwt = jwt;
        this.sessions = sessions;
        this.users = users;
    }

    @Transactional
    public TokenResponse issue(User user, String deviceId) {

        if (!user.enabled) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED
            );
        }

        if (deviceId != null && deviceId.length() > 255) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST
            );
        }

        return createPair(user.email, deviceId);
    }

    private TokenResponse createPair(
            String username,
            String deviceId
    ) {

        String accessToken = jwt.createAccess(username);
        String refreshToken = jwt.createRefresh(username);

        UserSession session = new UserSession();

        session.username = username;
        session.deviceId = deviceId;
        session.accessToken = accessToken;
        session.refreshToken = refreshToken;

        session.accessTokenExpiry = jwt
                .validateAccess(accessToken)
                .getExpiration()
                .toInstant();

        session.refreshTokenExpiry = jwt
                .validateRefresh(refreshToken)
                .getExpiration()
                .toInstant();

        session.status = SessionStatus.ACTIVE;

        sessions.saveAndFlush(session);

        return new TokenResponse(
                accessToken,
                refreshToken
        );
    }

    @Transactional
    public Optional<TokenResponse> refresh(String refreshToken) {

        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST
            );
        }

        Claims claims;

        try {

            claims = jwt.validateRefresh(refreshToken);

        } catch (JwtException | IllegalArgumentException e) {

            return Optional.empty();
        }

        UserSession session = sessions
                .findByRefreshToken(refreshToken)
                .orElse(null);

        if (session == null) {
            return Optional.empty();
        }

        User user = users
                .findByEmailIgnoreCase(session.username)
                .orElse(null);

        if (user == null
                || !user.enabled
                || user.passwordHash == null) {

            return Optional.empty();
        }

        if (!session.username.equals(claims.getSubject())
                || !session.username.equals(user.email)) {

            return Optional.empty();
        }

        if (!session.refreshTokenExpiry.isAfter(Instant.now())) {
            return Optional.empty();
        }

        if (session.status == SessionStatus.USED) {

            session.status = SessionStatus.REVOKED;

            sessions.saveAndFlush(session);

            return Optional.empty();
        }

        if (session.status != SessionStatus.ACTIVE) {
            return Optional.empty();
        }

        session.status = SessionStatus.USED;

        sessions.save(session);

        TokenResponse response = createPair(
                session.username,
                session.deviceId
        );

        return Optional.of(response);
    }
}
