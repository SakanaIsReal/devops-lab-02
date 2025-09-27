
package com.smartsplit.smartsplitback.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartsplit.smartsplitback.model.dto.AuthResponse;
import com.smartsplit.smartsplitback.security.JwtAuthFilter;
import com.smartsplit.smartsplitback.security.JwtService;
import com.smartsplit.smartsplitback.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SuppressWarnings("removal")
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

        @Autowired
        MockMvc mockMvc;
        @Autowired
        ObjectMapper objectMapper;

        @MockitoBean
        AuthService authService;
        @MockitoBean
        JwtAuthFilter jwtAuthFilter;
        @MockitoBean
        JwtService jwtService;

        @Test
        @DisplayName("register: 201 + accessToken in body")
        void register_shouldReturn201_andToken() throws Exception {
                var resp = new AuthResponse(
                                "fake-jwt-token",
                                "Bearer",
                                1L,
                                "alice@example.com",
                                "Alice",
                                0,
                                "https://example.com/alice-avatar.png", // Added avatarUrl
                                "0990000000", // Added phone
                                "https://example.com/alice-qr.png" // Added qrCodeUrl
                );
                when(authService.register(any())).thenReturn(resp);

                var body = Map.of(
                                "email", "alice@example.com",
                                "userName", "Alice",
                                "password", "Passw0rd!",
                                "phone", "0990000000");

                mockMvc.perform(
                                post("/api/auth/register")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(body)))
                                .andExpect(status().isCreated())
                                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                                .andExpect(jsonPath("$.accessToken").value("fake-jwt-token"))
                                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                                .andExpect(jsonPath("$.userId").value(1))
                                .andExpect(jsonPath("$.email").value("alice@example.com"))
                                .andExpect(jsonPath("$.userName").value("Alice"))
                                .andExpect(jsonPath("$.role").value(0));
        }

        @Test
        @DisplayName("register: 409 when email already in use")
        void register_shouldReturn409_whenEmailAlreadyUsed() throws Exception {
                when(authService.register(any()))
                                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use"));

                var body = Map.of(
                                "email", "alice@example.com",
                                "userName", "Alice",
                                "password", "Passw0rd!",
                                "phone", "0990000000");

                mockMvc.perform(
                                post("/api/auth/register")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(body)))
                                .andExpect(status().isConflict())
                                .andExpect(status().reason("Email already in use"));
        }

        @Test
        @DisplayName("login: 200 + accessToken in body")
        void login_shouldReturn200_andToken() throws Exception {
                var resp = new AuthResponse(
                                "fake-jwt-token-login",
                                "Bearer",
                                2L,
                                "bob@example.com",
                                "Bob",
                                0,
                                "https://example.com/bob-avatar.png", // Added avatarUrl
                                "0991111111", // Added phone
                                "https://example.com/bob-qr.png" // Added qrCodeUrl
                );
                when(authService.login(any())).thenReturn(resp);

                var body = Map.of(
                                "email", "bob@example.com",
                                "password", "P@ss1234");

                mockMvc.perform(
                                post("/api/auth/login")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(body)))
                                .andExpect(status().isOk())
                                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                                .andExpect(jsonPath("$.accessToken").value("fake-jwt-token-login"))
                                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                                .andExpect(jsonPath("$.userId").value(2))
                                .andExpect(jsonPath("$.email").value("bob@example.com"))
                                .andExpect(jsonPath("$.userName").value("Bob"))
                                .andExpect(jsonPath("$.role").value(0));
        }

        @Test
        @DisplayName("login: 401 when invalid credentials")
        void login_shouldReturn401_whenInvalidCredentials() throws Exception {
                when(authService.login(any()))
                                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

                var body = Map.of(
                                "email", "bob@example.com",
                                "password", "wrong-pass");

                mockMvc.perform(
                                post("/api/auth/login")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(body)))
                                .andExpect(status().isUnauthorized())
                                .andExpect(status().reason("Invalid credentials"));
        }
}
