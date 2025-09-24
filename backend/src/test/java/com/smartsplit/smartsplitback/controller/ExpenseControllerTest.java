
package com.smartsplit.smartsplitback.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartsplit.smartsplitback.model.*;
import com.smartsplit.smartsplitback.model.dto.ExpenseDto;
import com.smartsplit.smartsplitback.model.dto.ExpenseSettlementDto;
import com.smartsplit.smartsplitback.security.JwtAuthFilter;
import com.smartsplit.smartsplitback.security.JwtService;
import com.smartsplit.smartsplitback.security.Perms;
import com.smartsplit.smartsplitback.service.*;
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
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
@SuppressWarnings("removal")
@WebMvcTest(ExpenseController.class)
@AutoConfigureMockMvc(addFilters = false)

@Import({
        ExpenseControllerTest.MethodSecurityTestConfig.class,
        ExpenseControllerTest.SecurityExceptionHandler.class
})

class ExpenseControllerTest {

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    static class MethodSecurityTestConfig {}

    @RestControllerAdvice
    static class SecurityExceptionHandler {
        @ExceptionHandler({ AuthorizationDeniedException.class, AccessDeniedException.class })
        @ResponseStatus(HttpStatus.FORBIDDEN)
        void handleDenied() { /* no body */ }
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    // Controller dependencies
    @MockitoBean ExpenseService expenses;
    @MockitoBean GroupService groups;
    @MockitoBean UserService users;
    @MockitoBean ExpenseItemService itemService;
    @MockitoBean ExpensePaymentService paymentService;
    @MockitoBean ExpenseSettlementService settlementService;


    @MockitoBean JwtAuthFilter jwtAuthFilter;
    @MockitoBean JwtService jwtService;


    @MockitoBean(name = "perm", answers  = Answers.RETURNS_DEFAULTS)
    Perms perm;

    private Group g;
    private User payer;
    private Expense e;

    @BeforeEach
    void setUp() {

        when(perm.isAdmin()).thenReturn(true);
        when(perm.canViewExpense(anyLong())).thenReturn(true);
        when(perm.isGroupMember(anyLong())).thenReturn(true);
        when(perm.canCreateExpenseInGroup(anyLong())).thenReturn(true);
        when(perm.canManageExpense(anyLong())).thenReturn(true);

        g = new Group();
        g.setId(10L);
        g.setName("Trip");

        payer = new User();
        payer.setId(20L);
        payer.setUserName("Alice");

        e = new Expense();
        e.setId(100L);
        e.setGroup(g);
        e.setPayer(payer);
        e.setAmount(new BigDecimal("123.45"));
        e.setType(ExpenseType.values()[0]);
        e.setTitle("Dinner");
        e.setStatus(ExpenseStatus.values()[0]);
        e.setCreatedAt(LocalDateTime.of(2024, 1, 2, 3, 4, 5));
    }

    @Test @DisplayName("GET /api/expenses -> list all")
    void list_all() throws Exception {
        when(expenses.list()).thenReturn(List.of(e));

        mockMvc.perform(get("/api/expenses"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(100))
                .andExpect(jsonPath("$[0].groupId").value(10))
                .andExpect(jsonPath("$[0].payerUserId").value(20))
                .andExpect(jsonPath("$[0].amount").value(123.45))
                .andExpect(jsonPath("$[0].title").value("Dinner"));
    }

    @Test @DisplayName("GET /api/expenses?groupId=... -> list by group")
    void list_by_group() throws Exception {
        when(expenses.listByGroup(10L)).thenReturn(List.of(e));

        mockMvc.perform(get("/api/expenses").param("groupId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].groupId").value(10));
    }

    @Test @DisplayName("GET /api/expenses?payerUserId=... -> list by payer")
    void list_by_payer() throws Exception {
        when(expenses.listByPayer(20L)).thenReturn(List.of(e));

        mockMvc.perform(get("/api/expenses").param("payerUserId", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].payerUserId").value(20));
    }

    @Test @DisplayName("GET /api/expenses/{id} -> get by id")
    void get_by_id() throws Exception {
        when(expenses.get(100L)).thenReturn(e);

        mockMvc.perform(get("/api/expenses/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.title").value("Dinner"));
    }

    @Test @DisplayName("GET /api/expenses/group/{groupId} -> listByGroupForMember")
    void listByGroupForMember() throws Exception {
        when(expenses.listByGroup(10L)).thenReturn(List.of(e));

        mockMvc.perform(get("/api/expenses/group/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].groupId").value(10));
    }

    @Test @DisplayName("POST /api/expenses -> create")
    void create_expense() throws Exception {
        when(groups.get(10L)).thenReturn(g);
        when(users.get(20L)).thenReturn(payer);
        when(expenses.save(any(Expense.class))).thenReturn(e);

        var in = new ExpenseDto(
                null, 10L, 20L, new BigDecimal("123.45"),
                ExpenseType.values()[0], "Dinner",
                ExpenseStatus.values()[0], null
        );

        mockMvc.perform(
                        post("/api/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(in))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.groupId").value(10))
                .andExpect(jsonPath("$.payerUserId").value(20))
                .andExpect(jsonPath("$.title").value("Dinner"));
    }

    @Test @DisplayName("PUT /api/expenses/{id} -> update")
    void update_expense() throws Exception {
        when(expenses.get(100L)).thenReturn(e);
        when(expenses.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0, Expense.class));

        var patch = new ExpenseDto(
                null, null, null, new BigDecimal("200.00"),
                ExpenseType.values()[0], "Dinner+Drinks",
                ExpenseStatus.values()[0], null
        );

        mockMvc.perform(
                        put("/api/expenses/100")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(patch))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.amount").value(200.00))
                .andExpect(jsonPath("$.title").value("Dinner+Drinks"));
    }

    @Test @DisplayName("DELETE /api/expenses/{id} -> 204")
    void delete_expense() throws Exception {
        when(expenses.get(100L)).thenReturn(e);
        doNothing().when(expenses).delete(100L);

        mockMvc.perform(delete("/api/expenses/100"))
                .andExpect(status().isNoContent());
    }

    @Test @DisplayName("GET /api/expenses/{id}/total-items -> sumItems")
    void items_total() throws Exception {
        when(itemService.sumItems(100L)).thenReturn(new BigDecimal("321.00"));

        mockMvc.perform(get("/api/expenses/100/total-items"))
                .andExpect(status().isOk())
                .andExpect(content().string("321.00"));
    }

    @Test @DisplayName("GET /api/expenses/{id}/total-verified -> sumVerified")
    void verified_total() throws Exception {
        when(paymentService.sumVerified(100L)).thenReturn(new BigDecimal("111.11"));

        mockMvc.perform(get("/api/expenses/100/total-verified"))
                .andExpect(status().isOk())
                .andExpect(content().string("111.11"));
    }

    @Test @DisplayName("GET /api/expenses/{id}/summary -> itemsTotal & verifiedTotal")
    void summary() throws Exception {
        when(itemService.sumItems(100L)).thenReturn(new BigDecimal("50.00"));
        when(paymentService.sumVerified(100L)).thenReturn(new BigDecimal("20.00"));

        mockMvc.perform(get("/api/expenses/100/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemsTotal").value(50.00))
                .andExpect(jsonPath("$.verifiedTotal").value(20.00));
    }

    @Test @DisplayName("GET /api/expenses/{id}/settlement/{userId} -> single settlement")
    void settlement_by_user() throws Exception {
        var dto = makeSettlementDto(100L, 20L, new BigDecimal("33.33"));
        when(settlementService.userSettlement(100L, 20L)).thenReturn(dto);

        mockMvc.perform(get("/api/expenses/100/settlement/20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expenseId").value(100))
                .andExpect(jsonPath("$.userId").value(20))
                .andExpect(jsonPath("$.owedAmount").value(33.33));
    }

    @Test @DisplayName("GET /api/expenses/{id}/settlement -> list all settlements")
    void settlement_all() throws Exception {
        var s1 = makeSettlementDto(100L, 20L, new BigDecimal("10.00"));
        var s2 = makeSettlementDto(100L, 21L, new BigDecimal("15.50"));
        when(settlementService.allSettlements(100L)).thenReturn(List.of(s1, s2));

        mockMvc.perform(get("/api/expenses/100/settlement"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].owedAmount").value(10.00))
                .andExpect(jsonPath("$[1].owedAmount").value(15.50));
    }

    // ====== permission tests (403) ======

    @Test @DisplayName("GET /api/expenses/{id} -> 403 when cannot view expense")
    void get_by_id_forbidden_whenCannotView() throws Exception {
        when(perm.canViewExpense(100L)).thenReturn(false);

        mockMvc.perform(get("/api/expenses/100"))
                .andExpect(status().isForbidden());
    }

    @Test @DisplayName("GET /api/expenses/group/{groupId} -> 403 when not a group member")
    void listByGroupForMember_forbidden_whenNotMember() throws Exception {
        when(perm.isGroupMember(10L)).thenReturn(false);

        mockMvc.perform(get("/api/expenses/group/10"))
                .andExpect(status().isForbidden());
    }

    @Test @DisplayName("POST /api/expenses -> 403 when cannot create in group")
    void create_expense_forbidden_whenCannotCreateInGroup() throws Exception {
        when(perm.canCreateExpenseInGroup(10L)).thenReturn(false);

        var in = new ExpenseDto(
                null, 10L, 20L, new BigDecimal("123.45"),
                ExpenseType.values()[0], "Dinner",
                ExpenseStatus.values()[0], null
        );

        mockMvc.perform(
                        post("/api/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(in))
                )
                .andExpect(status().isForbidden());
    }

    @Test @DisplayName("PUT /api/expenses/{id} -> 403 when cannot manage expense")
    void update_expense_forbidden_whenCannotManage() throws Exception {
        when(perm.canManageExpense(100L)).thenReturn(false);

        var patch = new ExpenseDto(
                null, null, null, new BigDecimal("200.00"),
                ExpenseType.values()[0], "Dinner+Drinks",
                ExpenseStatus.values()[0], null
        );

        mockMvc.perform(
                        put("/api/expenses/100")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(patch))
                )
                .andExpect(status().isForbidden());
    }

    @Test @DisplayName("DELETE /api/expenses/{id} -> 403 when cannot manage expense")
    void delete_expense_forbidden_whenCannotManage() throws Exception {
        when(perm.canManageExpense(100L)).thenReturn(false);

        mockMvc.perform(delete("/api/expenses/100"))
                .andExpect(status().isForbidden());
    }

    @Test @DisplayName("GET /api/expenses/{id}/total-items -> 403 when cannot view expense")
    void items_total_forbidden_whenCannotView() throws Exception {
        when(perm.canViewExpense(100L)).thenReturn(false);

        mockMvc.perform(get("/api/expenses/100/total-items"))
                .andExpect(status().isForbidden());
    }

    @Test @DisplayName("GET /api/expenses/{id}/total-verified -> 403 when cannot view expense")
    void verified_total_forbidden_whenCannotView() throws Exception {
        when(perm.canViewExpense(100L)).thenReturn(false);

        mockMvc.perform(get("/api/expenses/100/total-verified"))
                .andExpect(status().isForbidden());
    }

    @Test @DisplayName("GET /api/expenses/{id}/summary -> 403 when cannot view expense")
    void summary_forbidden_whenCannotView() throws Exception {
        when(perm.canViewExpense(100L)).thenReturn(false);

        mockMvc.perform(get("/api/expenses/100/summary"))
                .andExpect(status().isForbidden());
    }

    @Test @DisplayName("GET /api/expenses/{id}/settlement/{userId} -> 403 when cannot view expense")
    void settlement_by_user_forbidden_whenCannotView() throws Exception {
        when(perm.canViewExpense(100L)).thenReturn(false);

        mockMvc.perform(get("/api/expenses/100/settlement/20"))
                .andExpect(status().isForbidden());
    }

    @Test @DisplayName("GET /api/expenses/{id}/settlement -> 403 when cannot view expense")
    void settlement_all_forbidden_whenCannotView() throws Exception {
        when(perm.canViewExpense(100L)).thenReturn(false);

        mockMvc.perform(get("/api/expenses/100/settlement"))
                .andExpect(status().isForbidden());
    }

    // ===== helper =====

    private ExpenseSettlementDto makeSettlementDto(Long expenseId, Long userId, BigDecimal amount) {
        try {
            Constructor<?> ctor = ExpenseSettlementDto.class.getDeclaredConstructors()[0];
            ctor.setAccessible(true);

            Class<?>[] types = ctor.getParameterTypes();
            Object[] args = new Object[types.length];

            boolean expenseIdSet = false, userIdSet = false, amountSet = false;

            for (int i = 0; i < types.length; i++) {
                Class<?> t = types[i];
                if ((t == Long.class || t == long.class) && !expenseIdSet) {
                    args[i] = expenseId; expenseIdSet = true;
                } else if ((t == Long.class || t == long.class) && !userIdSet) {
                    args[i] = userId; userIdSet = true;
                } else if (BigDecimal.class.isAssignableFrom(t) && !amountSet) {
                    args[i] = amount; amountSet = true;
                } else if (t == String.class) {
                    args[i] = "x";
                } else if (t == int.class) {
                    args[i] = 0;
                } else if (t == long.class) {
                    args[i] = 0L;
                } else if (t == boolean.class) {
                    args[i] = false;
                } else {
                    args[i] = null;
                }
            }
            return (ExpenseSettlementDto) ctor.newInstance(args);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to construct ExpenseSettlementDto reflectively", ex);
        }
    }
}
