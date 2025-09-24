
package com.smartsplit.smartsplitback.controller;

import com.smartsplit.smartsplitback.security.JwtAuthFilter;
import com.smartsplit.smartsplitback.security.JwtService;
import com.smartsplit.smartsplitback.security.Perms;
import com.smartsplit.smartsplitback.service.ExpenseItemShareService;
import org.junit.jupiter.api.BeforeEach;
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

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SuppressWarnings("removal")
@WebMvcTest(ExpenseItemShareController.class)
@AutoConfigureMockMvc(addFilters = false)

@Import({
        ExpenseItemShareControllerTest.MethodSecurityTestConfig.class,
        ExpenseItemShareControllerTest.MethodSecurityExceptionAdvice.class
})
class ExpenseItemShareControllerTest {

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

   
    @MockitoBean ExpenseItemShareService shares;


    @MockitoBean JwtAuthFilter jwtAuthFilter;
    @MockitoBean JwtService jwtService;

    @MockitoBean(name = "perm", answers = Answers.RETURNS_DEFAULTS)
    Perms perm;

    @BeforeEach
    void setupPermsDefaultsAllow() {
    
        when(perm.canViewExpenseItem(anyLong(), anyLong())).thenReturn(true);
        when(perm.canManageExpenseItem(anyLong(), anyLong())).thenReturn(true);
        when(perm.canManageExpenseShare(anyLong(), anyLong(), anyLong())).thenReturn(true);
    }

    // ---------- OK cases ----------

    @Test
    @DisplayName("GET /api/expenses/{expenseId}/items/{itemId}/shares -> 200 [] when no shares")
    void listByItem_empty() throws Exception {
        when(perm.canViewExpenseItem(100L, 200L)).thenReturn(true);
        when(shares.listByItemInExpense(100L, 200L)).thenReturn(List.of());

        mockMvc.perform(get("/api/expenses/100/items/200/shares"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("[]"));
    }

    @Test
    @DisplayName("DELETE share -> 204 No Content")
    void delete_share() throws Exception {
        when(perm.canManageExpenseShare(100L, 200L, 300L)).thenReturn(true);
        doNothing().when(shares).deleteShareInExpense(100L, 200L, 300L);

        mockMvc.perform(delete("/api/expenses/100/items/200/shares/300"))
                .andExpect(status().isNoContent());
    }

    // ---------- Validation (ตามข้อความ error เดิม) ----------

    @Test
    @DisplayName("POST add share -> 400 when both shareValue and sharePercent are missing")
    void add_missing_both_shareValue_and_sharePercent() throws Exception {
        when(perm.canManageExpenseItem(100L, 200L)).thenReturn(true);

        mockMvc.perform(
                        post("/api/expenses/100/items/200/shares")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("participantUserId", "5")
                )
                .andExpect(status().isBadRequest())
                .andExpect(status().reason("Either shareValue or sharePercent is required"));
    }

    @Test
    @DisplayName("POST add share -> 400 when sharePercent < 0")
    void add_invalid_percent_negative() throws Exception {
        when(perm.canManageExpenseItem(100L, 200L)).thenReturn(true);

        mockMvc.perform(
                        post("/api/expenses/100/items/200/shares")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("participantUserId", "5")
                                .param("sharePercent", "-1")
                )
                .andExpect(status().isBadRequest())
                .andExpect(status().reason("sharePercent must be between 0 and 100"));
    }

    @Test
    @DisplayName("POST add share -> 400 when sharePercent > 100")
    void add_invalid_percent_over_100() throws Exception {
        when(perm.canManageExpenseItem(100L, 200L)).thenReturn(true);

        mockMvc.perform(
                        post("/api/expenses/100/items/200/shares")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("participantUserId", "5")
                                .param("sharePercent", "101")
                )
                .andExpect(status().isBadRequest())
                .andExpect(status().reason("sharePercent must be between 0 and 100"));
    }

    @Test
    @DisplayName("PUT update share -> 400 when both shareValue and sharePercent are missing")
    void update_missing_both_shareValue_and_sharePercent() throws Exception {
        when(perm.canManageExpenseShare(100L, 200L, 300L)).thenReturn(true);

        mockMvc.perform(
                        put("/api/expenses/100/items/200/shares/300")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                )
                .andExpect(status().isBadRequest())
                .andExpect(status().reason("Either shareValue or sharePercent is required"));
    }

    @Test
    @DisplayName("PUT update share -> 400 when sharePercent < 0")
    void update_invalid_percent_negative() throws Exception {
        when(perm.canManageExpenseShare(100L, 200L, 300L)).thenReturn(true);

        mockMvc.perform(
                        put("/api/expenses/100/items/200/shares/300")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("sharePercent", "-0.01")
                )
                .andExpect(status().isBadRequest())
                .andExpect(status().reason("sharePercent must be between 0 and 100"));
    }

    @Test
    @DisplayName("PUT update share -> 400 when sharePercent > 100")
    void update_invalid_percent_over_100() throws Exception {
        when(perm.canManageExpenseShare(100L, 200L, 300L)).thenReturn(true);

        mockMvc.perform(
                        put("/api/expenses/100/items/200/shares/300")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("sharePercent", "100.01")
                )
                .andExpect(status().isBadRequest())
                .andExpect(status().reason("sharePercent must be between 0 and 100"));
    }

    // ---------- Permission (403) cases ----------

    @Test
    @DisplayName("GET shares -> 403 เมื่อไม่มีสิทธิ์ดูรายการของ item")
    void listByItem_forbidden_whenNoViewPermission() throws Exception {
        when(perm.canViewExpenseItem(100L, 200L)).thenReturn(false);

        mockMvc.perform(get("/api/expenses/100/items/200/shares"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST add share -> 403 เมื่อไม่มีสิทธิ์จัดการ item")
    void add_forbidden_whenCannotManageItem() throws Exception {
        when(perm.canManageExpenseItem(100L, 200L)).thenReturn(false);

        mockMvc.perform(
                        post("/api/expenses/100/items/200/shares")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("participantUserId", "5")
                                .param("sharePercent", "50")
                )
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT update share -> 403 เมื่อไม่มีสิทธิ์จัดการ share รายการนี้")
    void update_forbidden_whenCannotManageThisShare() throws Exception {
        // สมมติผ่านการเช็ค item (ถึงแม้ controller ของเราเช็คแค่ share)
        when(perm.canManageExpenseItem(100L, 200L)).thenReturn(true);
        when(perm.canManageExpenseShare(100L, 200L, 300L)).thenReturn(false);

        mockMvc.perform(
                        put("/api/expenses/100/items/200/shares/300")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("sharePercent", "50")
                )
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE share -> 403 เมื่อไม่มีสิทธิ์จัดการ share รายการนี้")
    void delete_forbidden_whenCannotManageThisShare() throws Exception {
        when(perm.canManageExpenseShare(100L, 200L, 300L)).thenReturn(false);

        mockMvc.perform(delete("/api/expenses/100/items/200/shares/300"))
                .andExpect(status().isForbidden());
    }
}
