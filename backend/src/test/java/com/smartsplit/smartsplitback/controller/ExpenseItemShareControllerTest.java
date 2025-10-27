package com.smartsplit.smartsplitback.controller;

import com.smartsplit.smartsplitback.model.ExpenseItem;
import com.smartsplit.smartsplitback.model.ExpenseItemShare;
import com.smartsplit.smartsplitback.model.User;
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
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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

    private ExpenseItemShare share(long shareId, long itemId, long userId,
                                   BigDecimal thb, BigDecimal percent) {
        ExpenseItemShare s = new ExpenseItemShare();
        s.setId(shareId);
        ExpenseItem item = new ExpenseItem();
        item.setId(itemId);
        s.setExpenseItem(item);
        User u = new User();
        u.setId(userId);
        s.setParticipant(u);
        s.setShareValue(thb);
        s.setSharePercent(percent);
        return s;
    }

    @Test
    @DisplayName("GET /api/expenses/{expenseId}/items/{itemId}/shares -> 200 [] when no shares")
    void listByItem_empty() throws Exception {
        when(shares.listByItemInExpense(100L, 200L)).thenReturn(List.of());

        mockMvc.perform(get("/api/expenses/100/items/200/shares"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("[]"));
    }

    @Test
    @DisplayName("GET shares -> 200 พร้อม 1 รายการ (ตรวจ id/expenseItemId/participantUserId/THB/percent)")
    void listByItem_one() throws Exception {
        when(shares.listByItemInExpense(100L, 200L))
                .thenReturn(List.of(share(300L, 200L, 5L,
                        new BigDecimal("447.19"), new BigDecimal("10.00"))));

        mockMvc.perform(get("/api/expenses/100/items/200/shares"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(300))
                .andExpect(jsonPath("$[0].expenseItemId").value(200))
                .andExpect(jsonPath("$[0].participantUserId").value(5))
                .andExpect(jsonPath("$[0].shareValue").value(447.19))
                .andExpect(jsonPath("$[0].sharePercent").value(10.00));
    }

    @Test
    @DisplayName("DELETE share -> 204 No Content")
    void delete_share() throws Exception {
        doNothing().when(shares).deleteShareInExpense(100L, 200L, 300L);

        mockMvc.perform(delete("/api/expenses/100/items/200/shares/300"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST add share (percent=0) -> 200 และ THB = 0.00")
    void add_percent_zero() throws Exception {
        when(shares.addShareInExpense(eq(100L), eq(200L), eq(5L), isNull(), any(BigDecimal.class)))
                .thenReturn(share(301L, 200L, 5L,
                        new BigDecimal("0.00"), new BigDecimal("0.00")));

        mockMvc.perform(
                        post("/api/expenses/100/items/200/shares")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("participantUserId", "5")
                                .param("sharePercent", "0")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(301))
                .andExpect(jsonPath("$.expenseItemId").value(200))
                .andExpect(jsonPath("$.participantUserId").value(5))
                .andExpect(jsonPath("$.shareValue").value(0.00))
                .andExpect(jsonPath("$.sharePercent").value(0.00));
    }

    @Test
    @DisplayName("POST add share (percent=100) -> 200")
    void add_percent_hundred() throws Exception {
        when(shares.addShareInExpense(eq(100L), eq(200L), eq(5L), isNull(), any(BigDecimal.class)))
                .thenReturn(share(302L, 200L, 5L,
                        new BigDecimal("36249.64"), new BigDecimal("100.00")));

        mockMvc.perform(
                        post("/api/expenses/100/items/200/shares")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("participantUserId", "5")
                                .param("sharePercent", "100")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(302))
                .andExpect(jsonPath("$.sharePercent").value(100.00));
    }

    @Test
    @DisplayName("POST add share (value=0, 0.01, 123.456) -> 200 และแสดงค่า THB/percent ตาม service")
    void add_value_various_scaling() throws Exception {
        when(shares.addShareInExpense(eq(100L), eq(200L), eq(5L), any(BigDecimal.class), isNull()))
                .thenReturn(share(303L, 200L, 5L, new BigDecimal("0.00"), null));

        mockMvc.perform(
                        post("/api/expenses/100/items/200/shares")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("participantUserId", "5")
                                .param("shareValue", "0")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(303))
                .andExpect(jsonPath("$.shareValue").value(0.00))
                .andExpect(jsonPath("$.sharePercent").doesNotExist());

        when(shares.addShareInExpense(eq(100L), eq(200L), eq(5L), any(BigDecimal.class), isNull()))
                .thenReturn(share(304L, 200L, 5L, new BigDecimal("0.36"), null));

        mockMvc.perform(
                        post("/api/expenses/100/items/200/shares")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("participantUserId", "5")
                                .param("shareValue", "0.01")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(304))
                .andExpect(jsonPath("$.shareValue").value(0.36));

        when(shares.addShareInExpense(eq(100L), eq(200L), eq(5L), any(BigDecimal.class), isNull()))
                .thenReturn(share(305L, 200L, 5L, new BigDecimal("4476.93"), null));

        mockMvc.perform(
                        post("/api/expenses/100/items/200/shares")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("participantUserId", "5")
                                .param("shareValue", "123.456")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(305))
                .andExpect(jsonPath("$.shareValue").value(4476.93));
    }

    @Test
    @DisplayName("PUT update share (percent 12.5) -> 200 และสะท้อนค่า THB ตาม service")
    void update_percent_mid() throws Exception {
        when(shares.updateShareInExpense(eq(100L), eq(200L), eq(300L), isNull(), any(BigDecimal.class)))
                .thenReturn(share(300L, 200L, 5L,
                        new BigDecimal("24.50"), new BigDecimal("12.50")));

        mockMvc.perform(
                        put("/api/expenses/100/items/200/shares/300")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("sharePercent", "12.5")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(300))
                .andExpect(jsonPath("$.sharePercent").value(12.50))
                .andExpect(jsonPath("$.shareValue").value(24.50));
    }

    @Test
    @DisplayName("PUT update share (value 12.345 -> ปัดใน service) -> 200")
    void update_value_scale() throws Exception {
        when(shares.updateShareInExpense(eq(100L), eq(200L), eq(300L), any(BigDecimal.class), isNull()))
                .thenReturn(share(300L, 200L, 5L,
                        new BigDecimal("447.19"), null));

        mockMvc.perform(
                        put("/api/expenses/100/items/200/shares/300")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("shareValue", "12.345")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shareValue").value(447.19));
    }

    @Test
    @DisplayName("POST add share -> 400 when both shareValue and sharePercent are missing")
    void add_missing_both_shareValue_and_sharePercent() throws Exception {
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
        mockMvc.perform(
                        put("/api/expenses/100/items/200/shares/300")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("sharePercent", "100.01")
                )
                .andExpect(status().isBadRequest())
                .andExpect(status().reason("sharePercent must be between 0 and 100"));
    }

    @Test
    @DisplayName("POST add share -> 400 เมื่อขาด participantUserId")
    void add_missing_participantUserId() throws Exception {
        mockMvc.perform(
                        post("/api/expenses/100/items/200/shares")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("sharePercent", "10")
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST add share -> 400 เมื่อ sharePercent ไม่ใช่ตัวเลข")
    void add_percent_not_number() throws Exception {
        mockMvc.perform(
                        post("/api/expenses/100/items/200/shares")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("participantUserId", "5")
                                .param("sharePercent", "abc")
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT update share -> 400 เมื่อ shareValue ไม่ใช่ตัวเลข")
    void update_value_not_number() throws Exception {
        mockMvc.perform(
                        put("/api/expenses/100/items/200/shares/300")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("shareValue", "xyz")
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST add share -> 404 เมื่อ service แจ้งไม่พบ item/expense")
    void add_not_found_from_service() throws Exception {
        when(shares.addShareInExpense(eq(100L), eq(200L), eq(5L), isNull(), any(BigDecimal.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found in this expense"));

        mockMvc.perform(
                        post("/api/expenses/100/items/200/shares")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("participantUserId", "5")
                                .param("sharePercent", "50")
                )
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT update share -> 404 เมื่อ service แจ้งไม่พบ share")
    void update_not_found_from_service() throws Exception {
        when(shares.updateShareInExpense(eq(100L), eq(200L), eq(300L), any(BigDecimal.class), isNull()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Share not found in this expense/item"));

        mockMvc.perform(
                        put("/api/expenses/100/items/200/shares/300")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("shareValue", "10.00")
                )
                .andExpect(status().isNotFound());
    }

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
