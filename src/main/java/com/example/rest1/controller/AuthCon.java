package com.example.rest1.controller;

import com.example.rest1.model.User;
import com.example.rest1.pip.RegisterReq;
import com.example.rest1.servic.AuthServ;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthCon {

    private final AuthServ service;

    public AuthCon(AuthServ service) {
        this.service = service;
    }

    @PostMapping("/register")
    public ResponseEntity<User> register(@RequestBody RegisterReq request) {

        User user = service.register(request);

        return ResponseEntity.status(201).body(user);
    }

    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken token) {
        return token;
    }
}