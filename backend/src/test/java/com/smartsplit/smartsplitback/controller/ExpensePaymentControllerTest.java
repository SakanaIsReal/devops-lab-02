
package com.smartsplit.smartsplitback.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartsplit.smartsplitback.model.ExpensePayment;
import com.smartsplit.smartsplitback.model.PaymentReceipt;
import com.smartsplit.smartsplitback.model.PaymentStatus;
import com.smartsplit.smartsplitback.model.User;
import com.smartsplit.smartsplitback.security.JwtAuthFilter;
import com.smartsplit.smartsplitback.security.JwtService;
import com.smartsplit.smartsplitback.security.Perms;
import com.smartsplit.smartsplitback.service.ExpensePaymentService;
import com.smartsplit.smartsplitback.service.FileStorageService;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SuppressWarnings("removal")
@WebMvcTest(ExpensePaymentController.class)
@AutoConfigureMockMvc(addFilters = false)

@Import({
        ExpensePaymentControllerTest.MethodSecurityTestConfig.class,
        ExpensePaymentControllerTest.MethodSecurityExceptionAdvice.class
})
class ExpensePaymentControllerTest {

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
    @Autowired ObjectMapper objectMapper;

    @MockitoBean ExpensePaymentService payments;
    @MockitoBean FileStorageService storage;


    @MockitoBean JwtAuthFilter jwtAuthFilter;
    @MockitoBean JwtService jwtService;


    @MockitoBean(name = "perm", answers = Answers.RETURNS_DEFAULTS)
    Perms perm;

    private static ExpensePayment payment(Long id, long fromUserId, BigDecimal amount, PaymentStatus status) {
        var p = new ExpensePayment();
        p.setId(id);
        var u = new User();
        u.setId(fromUserId);
        p.setFromUser(u);
        p.setAmount(amount);
        p.setStatus(status);
        return p;
    }


    @Test
    @DisplayName("GET /api/expenses/{expenseId}/payments -> list (200)")
    void list_payments() throws Exception {
        when(perm.canViewExpense(55L)).thenReturn(true);

        var p1 = payment(1L, 99L, new BigDecimal("100.00"), PaymentStatus.PENDING);
        var p2 = payment(2L, 98L, new BigDecimal("50.50"), PaymentStatus.VERIFIED);
        when(payments.listByExpense(55L)).thenReturn(List.of(p1, p2));

        mockMvc.perform(get("/api/expenses/55/payments"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].fromUserId").value(99))
                .andExpect(jsonPath("$[0].amount").value(100.00))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].fromUserId").value(98))
                .andExpect(jsonPath("$[1].amount").value(50.50))
                .andExpect(jsonPath("$[1].status").value("VERIFIED"));
    }

    @Test
    @DisplayName("GET /api/expenses/{expenseId}/payments/{paymentId} -> get in expense (found, 200)")
    void get_payment_found() throws Exception {
        when(perm.canViewExpense(55L)).thenReturn(true);

        var p = payment(10L, 77L, new BigDecimal("123.45"), PaymentStatus.PENDING);
        when(payments.getInExpense(55L, 10L)).thenReturn(p);

        mockMvc.perform(get("/api/expenses/55/payments/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.fromUserId").value(77))
                .andExpect(jsonPath("$.amount").value(123.45))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /api/expenses/{expenseId}/payments/{paymentId} -> 404 when not found")
    void get_payment_notFound() throws Exception {
        when(perm.canViewExpense(55L)).thenReturn(true);
        when(payments.getInExpense(55L, 999L)).thenReturn(null);

        mockMvc.perform(get("/api/expenses/55/payments/999"))
                .andExpect(status().isNotFound())
                .andExpect(status().reason("Payment not found in this expense"));
    }

    @Test
    @DisplayName("POST /api/expenses/{expenseId}/payments (multipart) -> create without receipt (201)")
    void create_payment_without_receipt() throws Exception {
        when(perm.canViewExpense(55L)).thenReturn(true);
        when(perm.canSubmitPayment(55L, 42L)).thenReturn(true);

        var created = payment(100L, 42L, new BigDecimal("88.00"), PaymentStatus.PENDING);
        when(payments.create(55L, 42L, new BigDecimal("88.00"))).thenReturn(created);

        mockMvc.perform(
                        multipart("/api/expenses/55/payments")
                                .param("fromUserId", "42")
                                .param("amount", "88.00")
                                .contentType(MediaType.MULTIPART_FORM_DATA)
                )
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.fromUserId").value(42))
                .andExpect(jsonPath("$.amount").value(88.00))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("POST /api/expenses/{expenseId}/payments (multipart) -> create with receipt (201)")
    void create_payment_with_receipt() throws Exception {
        when(perm.canViewExpense(55L)).thenReturn(true);
        when(perm.canSubmitPayment(55L, 50L)).thenReturn(true);

        var created = payment(101L, 50L, new BigDecimal("199.99"), PaymentStatus.PENDING);
        when(payments.create(55L, 50L, new BigDecimal("199.99"))).thenReturn(created);

        String receiptUrl = "http://files/receipt-payment-101.png";
        when(storage.save(any(), anyString(), anyString(), any())).thenReturn(receiptUrl);

        var withReceipt = payment(101L, 50L, new BigDecimal("199.99"), PaymentStatus.PENDING);
        var r = new PaymentReceipt();
        r.setFileUrl(receiptUrl);
        r.setPayment(withReceipt);
        withReceipt.setReceipt(r);
        when(payments.attachReceiptInExpense(eq(55L), eq(101L), anyString())).thenReturn(r);

        when(payments.getInExpense(55L, 101L)).thenReturn(withReceipt);

        MockMultipartFile receipt = new MockMultipartFile(
                "receipt", "slip.png", "image/png", new byte[]{1, 2, 3}
        );

        mockMvc.perform(
                        multipart("/api/expenses/55/payments")
                                .file(receipt)
                                .param("fromUserId", "50")
                                .param("amount", "199.99")
                                .contentType(MediaType.MULTIPART_FORM_DATA)
                )
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.fromUserId").value(50))
                .andExpect(jsonPath("$.amount").value(199.99))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(content().string(containsString(receiptUrl)));
    }

    @Test
    @DisplayName("PUT /api/expenses/{expenseId}/payments/{paymentId}/status -> update status (200)")
    void set_status() throws Exception {
        when(perm.canManageExpense(55L)).thenReturn(true);

        var updated = payment(200L, 70L, new BigDecimal("10.00"), PaymentStatus.VERIFIED);
        when(payments.setStatusInExpense(55L, 200L, PaymentStatus.VERIFIED)).thenReturn(updated);

        mockMvc.perform(
                        put("/api/expenses/55/payments/200/status")
                                .param("status", "VERIFIED")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(200))
                .andExpect(jsonPath("$.status").value("VERIFIED"));
    }

    @Test
    @DisplayName("DELETE /api/expenses/{expenseId}/payments/{paymentId} -> 204")
    void delete_payment() throws Exception {
        when(perm.canManageExpense(55L)).thenReturn(true);

        doNothing().when(payments).deleteInExpense(55L, 300L);

        mockMvc.perform(delete("/api/expenses/55/payments/300"))
                .andExpect(status().isNoContent());
    }

    // ---------- เคสสิทธิ์ (403) เพิ่มเติม ----------

    @Test
    @DisplayName("GET /api/expenses/{expenseId}/payments -> 403 เมื่อไม่มีสิทธิ์ดู expense")
    void list_forbidden_whenNoPermission() throws Exception {
        when(perm.canViewExpense(55L)).thenReturn(false);

        mockMvc.perform(get("/api/expenses/55/payments"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/expenses/{expenseId}/payments/{paymentId} -> 403 เมื่อไม่มีสิทธิ์ดู expense")
    void get_forbidden_whenNoPermission() throws Exception {
        when(perm.canViewExpense(55L)).thenReturn(false);

        mockMvc.perform(get("/api/expenses/55/payments/10"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/expenses/{expenseId}/payments -> 403 เมื่อไม่มีสิทธิ์ submit")
    void create_forbidden_whenCannotSubmit() throws Exception {
        when(perm.canViewExpense(55L)).thenReturn(true);
        when(perm.canSubmitPayment(55L, 50L)).thenReturn(false);

        mockMvc.perform(
                        multipart("/api/expenses/55/payments")
                                .param("fromUserId", "50")
                                .param("amount", "199.99")
                                .contentType(MediaType.MULTIPART_FORM_DATA)
                )
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /api/expenses/{expenseId}/payments/{paymentId}/status -> 403 เมื่อไม่มีสิทธิ์ manage")
    void set_status_forbidden_whenCannotManage() throws Exception {
        when(perm.canManageExpense(55L)).thenReturn(false);

        mockMvc.perform(
                        put("/api/expenses/55/payments/200/status")
                                .param("status", "VERIFIED")
                )
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /api/expenses/{expenseId}/payments/{paymentId} -> 403 เมื่อไม่มีสิทธิ์ manage")
    void delete_forbidden_whenCannotManage() throws Exception {
        when(perm.canManageExpense(55L)).thenReturn(false);

        mockMvc.perform(delete("/api/expenses/55/payments/300"))
                .andExpect(status().isForbidden());
    }
}
