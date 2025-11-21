// src/main/java/com/smartsplit/smartsplitback/service/AuthService.java
package com.smartsplit.smartsplitback.service;

import com.smartsplit.smartsplitback.model.Role;
import com.smartsplit.smartsplitback.model.User;
import com.smartsplit.smartsplitback.model.dto.*;
import com.smartsplit.smartsplitback.repository.UserRepository;
import com.smartsplit.smartsplitback.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    public AuthResponse register(RegisterRequest req) {
        users.findByEmail(req.email()).ifPresent(u -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
        });

        User u = new User();
        u.setEmail(req.email());
        u.setUserName(req.userName());
        u.setPhone(req.phone());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setRole(Role.USER);

        if (req.firstName() != null && !req.firstName().isBlank()) {
            u.setFirstName(req.firstName().trim());
        } else {
            u.setFirstName(null);
        }
        if (req.lastName() != null && !req.lastName().isBlank()) {
            u.setLastName(req.lastName().trim());
        } else {
            u.setLastName(null);
        }

        users.save(u);

        int roleCode = u.getRole().code();

        String token = jwt.generate(
                u.getId().toString(),
                Map.of(
                        "uid", u.getId(),
                        "email", u.getEmail(),
                        "userName", u.getUserName(),
                        "role", roleCode
                ),
                60 * 60 * 24
        );

        return new AuthResponse(
                token, "Bearer",
                u.getId(), u.getEmail(), u.getUserName(),
                roleCode,
                u.getPhone(), u.getAvatarUrl(), u.getQrCodeUrl()
        );
    }

    public AuthResponse login(LoginRequest req) {
        User u = users.findByEmail(req.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!encoder.matches(req.password(), u.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        int roleCode = u.getRole().code();

        String token = jwt.generate(
                u.getId().toString(),
                Map.of(
                        "uid", u.getId(),
                        "email", u.getEmail(),
                        "userName", u.getUserName(),
                        "role", roleCode
                ),
                60 * 60 * 24
        );

        return new AuthResponse(
                token, "Bearer",
                u.getId(), u.getEmail(), u.getUserName(),
                roleCode,
                u.getPhone(), u.getAvatarUrl(), u.getQrCodeUrl()
        );
    }
}
