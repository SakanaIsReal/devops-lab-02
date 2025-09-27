// src/test/java/com/smartsplit/smartsplitback/controller/UserBalanceControllerTest.java
package com.smartsplit.smartsplitback.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartsplit.smartsplitback.security.JwtAuthFilter;
import com.smartsplit.smartsplitback.security.JwtService;
import com.smartsplit.smartsplitback.security.Perms;
import com.smartsplit.smartsplitback.service.UserBalanceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

@SuppressWarnings("removal")
@WebMvcTest(controllers = UserBalanceController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({UserBalanceControllerTest.MethodSecurityConfig.class, UserBalanceControllerTest.MethodSecurityAdvice.class})
class UserBalanceControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfig {}

    @RestControllerAdvice
    static class MethodSecurityAdvice {
        @ExceptionHandler({
                AuthorizationDeniedException.class,
                AuthenticationCredentialsNotFoundException.class
        })
        @ResponseStatus(HttpStatus.FORBIDDEN)
        void handle() {}
    }


    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;


    @MockitoBean JwtAuthFilter jwtAuthFilter;
    @MockitoBean JwtService jwtService;


    @MockitoBean(name = "perm", answers = Answers.RETURNS_DEFAULTS)
    Perms perm;

    @MockitoBean UserBalanceService svc;

    // --------- LIST ---------
    @Test
    @DisplayName("GET /api/me/balances -> 403 เมื่อยังไม่ authenticated")
    void list_forbidden_when_not_authenticated() throws Exception {
        // ไม่มี @WithMockUser => ไม่ผ่าน @PreAuthorize("isAuthenticated()") => 403
        mockMvc.perform(get("/api/me/balances"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/me/balances -> 401 เมื่อ perm.currentUserId() = null, 200 เมื่อสำเร็จ")
    void list_unauthorized_then_ok() throws Exception {
        // ผ่าน @PreAuthorize แล้ว แต่ไม่มี user id ในระบบ (เช่น token เพี้ยน/ว่าง) => 401
        when(perm.currentUserId()).thenReturn(null);
        mockMvc.perform(get("/api/me/balances"))
                .andExpect(status().isUnauthorized())
                .andExpect(status().reason("Unauthorized"));

        // ปกติ: มี user id, service คืนลิสต์ว่าง
        when(perm.currentUserId()).thenReturn(77L);
        when(svc.listBalances(77L)).thenReturn(List.of()); // [] ว่าง
        mockMvc.perform(get("/api/me/balances"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // --------- SUMMARY ---------
    @Test
    @DisplayName("GET /api/me/balances/summary -> 403 เมื่อยังไม่ authenticated")
    void summary_forbidden_when_not_authenticated() throws Exception {
        mockMvc.perform(get("/api/me/balances/summary"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/me/balances/summary -> 401 เมื่อ perm.currentUserId() = null, 200 เมื่อสำเร็จ")
    void summary_unauthorized_then_ok() throws Exception {
        when(perm.currentUserId()).thenReturn(null);
        mockMvc.perform(get("/api/me/balances/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(status().reason("Unauthorized"));

        // ปกติ: คืน object summary อะไรก็ได้ (mock class ตรง ๆ)
        when(perm.currentUserId()).thenReturn(88L);
        Object summaryMock = Mockito.mock(Class.forName("com.smartsplit.smartsplitback.model.dto.BalanceSummaryDto"));
        when(svc.summary(88L)).thenReturn((com.smartsplit.smartsplitback.model.dto.BalanceSummaryDto) summaryMock);

        mockMvc.perform(get("/api/me/balances/summary"))
                .andExpect(status().isOk());

    }
}
