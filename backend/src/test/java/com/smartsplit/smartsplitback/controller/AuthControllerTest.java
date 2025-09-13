// src/test/java/com/smartsplit/smartsplitback/controller/AuthControllerTest.java
package com.smartsplit.smartsplitback.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartsplit.smartsplitback.model.dto.AuthResponse;
import com.smartsplit.smartsplitback.security.JwtAuthFilter;
import com.smartsplit.smartsplitback.security.JwtService;
import com.smartsplit.smartsplitback.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;  // ใช้ต่อไปได้ แม้จะ deprecated (ยังใช้ได้ใน Boot 3.5)
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SuppressWarnings("removal")
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    // mock ชั้น service ที่ controller เรียกใช้
    @MockBean AuthService authService;

    // mock ฝั่ง security เพื่อไม่ให้ context ไปสร้างของจริง
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean JwtService jwtService;

    @Test
    void register_shouldReturn201_andToken() throws Exception {
        var resp = new AuthResponse(
                "fake-jwt-token",
                "Bearer",
                1L,
                "alice@example.com",
                "Alice",
                0
        );
        when(authService.register(any())).thenReturn(resp);

        var body = Map.of(
                "email", "alice@example.com",
                "userName", "Alice",
                "password", "Passw0rd!",
                "phone", "0990000000"
        );

        mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body))
                )
                .andExpect(status().isCreated())
                .andExpect(content().string(containsString("fake-jwt-token")));
    }

    @Test
    void login_shouldReturn200_andToken() throws Exception {
        var resp = new AuthResponse(
                "fake-jwt-token-login",
                "Bearer",
                2L,
                "bob@example.com",
                "Bob",
                0
        );
        when(authService.login(any())).thenReturn(resp);

        var body = Map.of(
                "email", "bob@example.com",
                "password", "P@ss1234"
        );

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body))
                )
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("fake-jwt-token-login")));
    }
}
