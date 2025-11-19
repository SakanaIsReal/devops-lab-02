package com.smartsplit.smartsplitback.controller;

import com.smartsplit.smartsplitback.model.dto.*;
import com.smartsplit.smartsplitback.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import io.micrometer.tracing.annotation.NewSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @NewSpan("auth.register.controller")
    public AuthResponse register(@RequestBody RegisterRequest req) {
        return auth.register(req);
    }

    @PostMapping("/login")
    @NewSpan("auth.login.controller")
    public AuthResponse login(@RequestBody LoginRequest req) {
        return auth.login(req);
    }
}
