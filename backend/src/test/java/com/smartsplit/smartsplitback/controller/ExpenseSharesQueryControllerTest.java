
package com.smartsplit.smartsplitback.controller;

import com.smartsplit.smartsplitback.model.ExpenseItemShare;
import com.smartsplit.smartsplitback.security.JwtAuthFilter;
import com.smartsplit.smartsplitback.security.JwtService;
import com.smartsplit.smartsplitback.security.Perms;
import com.smartsplit.smartsplitback.service.ExpenseItemShareService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SuppressWarnings("removal")
@WebMvcTest(controllers = ExpenseSharesQueryController.class)
@AutoConfigureMockMvc(addFilters = false)

@Import({ExpenseSharesQueryControllerTest.MethodSecurityTestConfig.class,
        ExpenseSharesQueryControllerTest.MethodSecurityExceptionAdvice.class})
class ExpenseSharesQueryControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {}

   
    @RestControllerAdvice
    static class MethodSecurityExceptionAdvice {
        @ExceptionHandler(AuthorizationDeniedException.class)
        @ResponseStatus(HttpStatus.FORBIDDEN)
        void handle() {}
    }

    @Autowired MockMvc mockMvc;


    @MockitoBean JwtAuthFilter jwtAuthFilter;
    @MockitoBean JwtService jwtService;

    @MockitoBean(name = "perm", answers = Answers.RETURNS_DEFAULTS)
    Perms perm;

    @MockitoBean ExpenseItemShareService shares;

    @Test
    @DisplayName("GET /api/expenses/{expenseId}/shares -> 200 OK เมื่อมีสิทธิ์")
    void listByExpense_ok() throws Exception {
        when(perm.canViewExpense(55L)).thenReturn(true);
        when(shares.listByExpense(55L)).thenReturn(List.of());

        mockMvc.perform(get("/api/expenses/55/shares"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/expenses/{expenseId}/shares/mine -> 200 OK เมื่อมีสิทธิ์และล็อกอิน")
    void listMine_ok() throws Exception {
        when(perm.canViewExpense(55L)).thenReturn(true);
        when(perm.currentUserId()).thenReturn(77L);
        when(shares.listByExpenseAndParticipant(55L, 77L)).thenReturn(List.of());

        mockMvc.perform(get("/api/expenses/55/shares/mine"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/expenses/{expenseId}/shares/mine -> 401 เมื่อยังไม่ล็อกอิน")
    void listMine_unauthorized_whenNoUser() throws Exception {
        when(perm.canViewExpense(55L)).thenReturn(true);
        when(perm.currentUserId()).thenReturn(null);

        mockMvc.perform(get("/api/expenses/55/shares/mine"))
                .andExpect(status().isUnauthorized())
                .andExpect(status().reason("Unauthorized"));
    }

    @Test
    @DisplayName("GET /api/expenses/{expenseId}/shares -> 403 เมื่อไม่มีสิทธิ์")
    void listByExpense_forbidden_whenNoPermission() throws Exception {
        when(perm.canViewExpense(55L)).thenReturn(false);

        mockMvc.perform(get("/api/expenses/55/shares"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/expenses/{expenseId}/shares/mine -> 403 เมื่อไม่มีสิทธิ์ (แม้ล็อกอิน)")
    void listMine_forbidden_whenNoPermission() throws Exception {
        when(perm.canViewExpense(55L)).thenReturn(false);
        when(perm.currentUserId()).thenReturn(77L);

        mockMvc.perform(get("/api/expenses/55/shares/mine"))
                .andExpect(status().isForbidden());
    }
}
