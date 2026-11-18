
package com.example.rest1.controller;

import com.example.rest1.pip.LoginReq;
import com.example.rest1.pip.RegisterReq;
import com.example.rest1.pip.RefreshReq;
import com.example.rest1.pip.TokenResponse;
import com.example.rest1.servic.AuthServ;
import com.example.rest1.servic.TokenServ;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/auth", "/api/auth"})
public class AuthCon {

    private final AuthServ service;
    private final TokenServ tokens;

    public AuthCon(
            AuthServ service,
            TokenServ tokens
    ) {
        this.service = service;
        this.tokens = tokens;
    }

    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(
            @RequestBody RegisterReq request
    ) {

        TokenResponse response = service.register(request);

        return ResponseEntity.status(201).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(
            @RequestBody LoginReq request
    ) {

        return ResponseEntity.ok(
                service.login(request)
        );
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @RequestBody RefreshReq request
    ) {

        return tokens.refresh(request.refreshToken())
                .map(ResponseEntity::ok)
                .orElseGet(() ->
                        ResponseEntity.status(401).build()
                );
    }
}
