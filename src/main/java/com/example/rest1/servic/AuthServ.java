
package com.example.rest1.servic;

import com.example.rest1.model.Role;
import com.example.rest1.model.User;
import com.example.rest1.pip.RegisterReq;
import com.example.rest1.repo.UserRepo;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class AuthServ {

    private final UserRepo users;
    private final PasswordEncoder passwordEncoder;

    public AuthServ(
            UserRepo users,
            PasswordEncoder passwordEncoder
    ) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    public User register(RegisterReq request) {

        if (request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Name is required"
            );
        }

        if (request.email() == null || request.email().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Email is required"
            );
        }

        String email = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid email"
            );
        }

        String password = request.password();

        if (password == null
                || password.length() < 10
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || !password.matches(".*[A-Z].*")
                || !password.matches(".*[a-z].*")
                || !password.matches(".*[0-9].*")) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password is too weak"
            );
        }

        if (users.existsByEmailIgnoreCase(email)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email already exists"
            );
        }

        User user = new User();

        user.name = request.name().trim();
        user.email = email;
        user.passwordHash = passwordEncoder.encode(password);
        user.role = Role.USER;
        user.enabled = true;

        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot register user"
            );
        }
    }
}
