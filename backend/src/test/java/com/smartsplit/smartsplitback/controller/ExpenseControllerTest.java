package com.smartsplit.smartsplitback.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartsplit.smartsplitback.model.*;
import com.smartsplit.smartsplitback.model.dto.ExpenseDto;
import com.smartsplit.smartsplitback.model.dto.ExpenseItemDto;
import com.smartsplit.smartsplitback.model.dto.ExpenseSettlementDto;
import com.smartsplit.smartsplitback.repository.ExpenseRepository;
import com.smartsplit.smartsplitback.security.JwtAuthFilter;
import com.smartsplit.smartsplitback.security.JwtService;
import com.smartsplit.smartsplitback.security.Perms;
import com.smartsplit.smartsplitback.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {
        ExpenseController.class,
        ExpenseItemController.class,
        ExpenseItemShareController.class
})
@AutoConfigureMockMvc(addFilters = false)
@Import({
        ExpenseApiWebMvcTest.MethodSecurityTestConfig.class,
        ExpenseApiWebMvcTest.SecurityExceptionHandler.class
})
@WithMockUser(username = "test-user", roles = {"USER"})
class ExpenseApiWebMvcTest {

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    static class MethodSecurityTestConfig {}

    @RestControllerAdvice
    static class SecurityExceptionHandler {
        @ExceptionHandler({ AuthorizationDeniedException.class, AccessDeniedException.class })
        @ResponseStatus(HttpStatus.FORBIDDEN)
        void handleDenied() {}
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean ExpenseService expenses;
    @MockitoBean GroupService groups;
    @MockitoBean UserService users;
    @MockitoBean ExpenseItemService itemService;
    @MockitoBean ExpenseItemShareService shareService;
    @MockitoBean ExpensePaymentService paymentService;
    @MockitoBean ExpenseSettlementService settlementService;
    @MockitoBean ExpenseExportService exportService;
    @MockitoBean ExchangeRateService fx;
    @MockitoBean ExpenseRepository expenseRepository;

    @MockitoBean JwtAuthFilter jwtAuthFilter;
    @MockitoBean JwtService jwtService;

    @MockitoBean(name = "perm", answers = Answers.RETURNS_DEFAULTS)
    Perms perm;

    private Group g;
    private User payer;
    private Expense e;

    private ExpenseItem itemUsd;
    private ExpenseItem itemJpy;
    private ExpenseItem itemThb;

    @BeforeEach
    void setUp() {
        when(perm.isAdmin()).thenReturn(true);
        when(perm.canViewExpense(anyLong())).thenReturn(true);
        when(perm.isGroupMember(anyLong())).thenReturn(true);
        when(perm.canCreateExpenseInGroup(any())).thenReturn(true);
        when(perm.canManageExpense(anyLong())).thenReturn(true);
        when(perm.canViewExpenseItem(anyLong(), anyLong())).thenReturn(true);
        when(perm.canManageExpenseItem(anyLong(), anyLong())).thenReturn(true);
        when(perm.canManageExpenseShare(anyLong(), anyLong(), anyLong())).thenReturn(true);
        when(perm.currentUserId()).thenReturn(999L);

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
        e.setType(ExpenseType.EQUAL);
        e.setTitle("Dinner");
        e.setStatus(ExpenseStatus.OPEN);
        e.setCreatedAt(LocalDateTime.of(2024, 1, 2, 3, 4, 5));
        e.setExchangeRatesJson("{\"THB\":1,\"USD\":36.25,\"JPY\":0.245,\"EUR\":39.90}");

        itemUsd = new ExpenseItem();
        itemUsd.setId(1001L);
        itemUsd.setExpense(e);
        itemUsd.setName("Burger");
        itemUsd.setAmount(new BigDecimal("10.00"));
        itemUsd.setCurrency("USD");

        itemJpy = new ExpenseItem();
        itemJpy.setId(1002L);
        itemJpy.setExpense(e);
        itemJpy.setName("Sushi");
        itemJpy.setAmount(new BigDecimal("1000"));
        itemJpy.setCurrency("JPY");

        itemThb = new ExpenseItem();
        itemThb.setId(1003L);
        itemThb.setExpense(e);
        itemThb.setName("Water");
        itemThb.setAmount(new BigDecimal("50.00"));
        itemThb.setCurrency("THB");
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

    @Test @DisplayName("GET /api/expenses/{id} -> 404 when not found")
    void get_by_id_not_found() throws Exception {
        when(expenses.get(999L)).thenReturn(null);

        mockMvc.perform(get("/api/expenses/999"))
                .andExpect(status().isNotFound());
    }

    @Test @DisplayName("GET /api/expenses/group/{groupId} -> listByGroupForMember")
    void listByGroupForMember() throws Exception {
        when(expenses.listByGroup(10L)).thenReturn(List.of(e));

        mockMvc.perform(get("/api/expenses/group/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].groupId").value(10));
    }

    @Test @DisplayName("POST /api/expenses -> create (lock FX rates)")
    void create_expense_with_fx_lock() throws Exception {
        when(groups.get(10L)).thenReturn(g);
        when(users.get(20L)).thenReturn(payer);
        when(fx.getLiveRatesToThb()).thenReturn(Map.of("THB", BigDecimal.ONE, "USD", new BigDecimal("36.25")));
        when(expenses.save(any(Expense.class))).thenAnswer(inv -> {
            Expense arg = inv.getArgument(0);
            arg.setId(100L);
            return arg;
        });

        var in = new ExpenseDto(
                null, 10L, 20L, new BigDecimal("123.45"),
                ExpenseType.EQUAL, "Dinner", ExpenseStatus.OPEN, null
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

        verify(fx, times(1)).getLiveRatesToThb();

        ArgumentCaptor<Expense> cap = ArgumentCaptor.forClass(Expense.class);
        verify(expenses).save(cap.capture());
        String json = cap.getValue().getExchangeRatesJson();
        org.assertj.core.api.Assertions.assertThat(json).contains("THB");
    }

    @Test @DisplayName("POST /api/expenses -> create (FX fallback to {THB:1} when fx fails)")
    void create_expense_fx_fallback() throws Exception {
        when(groups.get(10L)).thenReturn(g);
        when(users.get(20L)).thenReturn(payer);
        when(fx.getLiveRatesToThb()).thenThrow(new RuntimeException("fx down"));
        when(expenses.save(any(Expense.class))).thenAnswer(inv -> {
            Expense arg = inv.getArgument(0);
            arg.setId(101L);
            return arg;
        });

        var in = new ExpenseDto(
                null, 10L, 20L, new BigDecimal("50.00"),
                ExpenseType.EQUAL, "Snack", ExpenseStatus.OPEN, null
        );

        mockMvc.perform(
                        post("/api/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(in))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(101));

        ArgumentCaptor<Expense> cap = ArgumentCaptor.forClass(Expense.class);
        verify(expenses).save(cap.capture());
        String json = cap.getValue().getExchangeRatesJson();
        org.assertj.core.api.Assertions.assertThat(json).contains("\"THB\":1");
    }

    @Test @DisplayName("POST /api/expenses -> 400 when missing groupId/payerUserId")
    void create_expense_bad_request() throws Exception {
        var in = new ExpenseDto(
                null, null, null, new BigDecimal("123.45"),
                ExpenseType.EQUAL, "Dinner", ExpenseStatus.OPEN, null
        );

        mockMvc.perform(
                        post("/api/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(in))
                )
                .andExpect(status().isBadRequest());
    }

    @Test @DisplayName("PUT /api/expenses/{id} -> update")
    void update_expense() throws Exception {
        when(expenses.get(100L)).thenReturn(e);
        when(expenses.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

        var patch = new ExpenseDto(
                null, null, null, new BigDecimal("200.00"),
                ExpenseType.EQUAL, "Dinner+Drinks", ExpenseStatus.OPEN, null
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

    @Test @DisplayName("PUT /api/expenses/{id} -> 404 when expense not found")
    void update_expense_not_found() throws Exception {
        when(expenses.get(404L)).thenReturn(null);

        var patch = new ExpenseDto(
                null, null, null, new BigDecimal("1.00"),
                ExpenseType.EQUAL, "x", ExpenseStatus.OPEN, null
        );

        mockMvc.perform(
                        put("/api/expenses/404")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(patch))
                )
                .andExpect(status().isNotFound());
    }

    @Test @DisplayName("DELETE /api/expenses/{id} -> 204")
    void delete_expense() throws Exception {
        when(expenses.get(100L)).thenReturn(e);
        doNothing().when(expenses).delete(100L);

        mockMvc.perform(delete("/api/expenses/100"))
                .andExpect(status().isNoContent());
    }

    @Test @DisplayName("DELETE /api/expenses/{id} -> 404 when not found")
    void delete_expense_not_found() throws Exception {
        when(expenses.get(404L)).thenReturn(null);

        mockMvc.perform(delete("/api/expenses/404"))
                .andExpect(status().isNotFound());
    }

    @Test @DisplayName("GET /api/expenses/{id}/total-items -> convert each item to THB and sum")
    void items_total_multi_currency() throws Exception {
        when(expenses.get(100L)).thenReturn(e);
        when(itemService.listByExpense(100L)).thenReturn(List.of(itemUsd, itemJpy, itemThb));

        Map<String, BigDecimal> rates = Map.of(
                "USD", new BigDecimal("36.25"),
                "JPY", new BigDecimal("0.245"),
                "THB", BigDecimal.ONE
        );
        when(fx.getRatesToThb(e)).thenReturn(rates);
        when(fx.toThb(eq("USD"), eq(new BigDecimal("10.00")), eq(rates)))
                .thenReturn(new BigDecimal("362.50"));
        when(fx.toThb(eq("JPY"), eq(new BigDecimal("1000")), eq(rates)))
                .thenReturn(new BigDecimal("245.00"));
        when(fx.toThb(eq("THB"), eq(new BigDecimal("50.00")), eq(rates)))
                .thenReturn(new BigDecimal("50.00"));

        mockMvc.perform(get("/api/expenses/100/total-items"))
                .andExpect(status().isOk())
                .andExpect(content().string("657.50"));
    }

    @Test @DisplayName("GET /api/expenses/{id}/total-items -> 0 when no items")
    void items_total_no_items() throws Exception {
        when(expenses.get(100L)).thenReturn(e);
        when(itemService.listByExpense(100L)).thenReturn(List.of());

        mockMvc.perform(get("/api/expenses/100/total-items"))
                .andExpect(status().isOk())
                .andExpect(content().string("0"));
    }

    @Test @DisplayName("GET /api/expenses/{id}/total-items -> 404 when expense not found")
    void items_total_not_found() throws Exception {
        when(expenses.get(404L)).thenReturn(null);

        mockMvc.perform(get("/api/expenses/404/total-items"))
                .andExpect(status().isNotFound());
    }

    @Test @DisplayName("GET /api/expenses/{id}/total-verified -> sumVerified (pass-through)")
    void verified_total() throws Exception {
        when(paymentService.sumVerified(100L)).thenReturn(new BigDecimal("111.11"));
        when(perm.canViewExpense(100L)).thenReturn(true);

        mockMvc.perform(get("/api/expenses/100/total-verified"))
                .andExpect(status().isOk())
                .andExpect(content().string("111.11"));
    }

    @Test @DisplayName("GET /api/expenses/{id}/summary -> itemsTotal & verifiedTotal (THB)")
    void summary() throws Exception {
        when(expenses.get(100L)).thenReturn(e);

        ExpenseItem i1 = new ExpenseItem();
        i1.setAmount(new BigDecimal("5"));
        i1.setCurrency("USD");

        ExpenseItem i2 = new ExpenseItem();
        i2.setAmount(new BigDecimal("200"));
        i2.setCurrency("THB");

        when(itemService.listByExpense(100L)).thenReturn(List.of(i1, i2));

        Map<String, BigDecimal> rates = Map.of(
                "USD", new BigDecimal("36.25"),
                "THB", BigDecimal.ONE
        );
        when(fx.getRatesToThb(e)).thenReturn(rates);
        when(fx.toThb(eq("USD"), eq(new BigDecimal("5")), eq(rates)))
                .thenReturn(new BigDecimal("181.25"));
        when(fx.toThb(eq("THB"), eq(new BigDecimal("200")), eq(rates)))
                .thenReturn(new BigDecimal("200"));

        when(paymentService.sumVerified(100L)).thenReturn(new BigDecimal("20.00"));

        mockMvc.perform(get("/api/expenses/100/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemsTotal").value(381.25))
                .andExpect(jsonPath("$.verifiedTotal").value(20.00));
    }

    @Test @DisplayName("GET /api/expenses/{id}/summary -> 404 when expense not found")
    void summary_not_found() throws Exception {
        when(expenses.get(404L)).thenReturn(null);

        mockMvc.perform(get("/api/expenses/404/summary"))
                .andExpect(status().isNotFound());
    }

    @Test @DisplayName("GET /api/expenses/participating -> list expenses I participate in (requires isAuthenticated())")
    void my_shared_expenses() throws Exception {
        Expense e2 = new Expense();
        e2.setId(200L);
        e2.setGroup(g);
        e2.setPayer(payer);
        e2.setAmount(new BigDecimal("10.00"));
        e2.setType(ExpenseType.EQUAL);
        e2.setTitle("Taxi");
        e2.setStatus(ExpenseStatus.OPEN);
        e2.setCreatedAt(LocalDateTime.now());

        when(expenses.listByParticipant(999L)).thenReturn(List.of(e, e2));

        mockMvc.perform(get("/api/expenses/participating"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(100))
                .andExpect(jsonPath("$[1].id").value(200));
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

    @Test @DisplayName("GET /api/expenses/{id}/export.pdf -> returns PDF bytes with header")
    void export_pdf() throws Exception {
        when(expenses.get(100L)).thenReturn(e);
        when(exportService.renderExpensePdf(100L)).thenReturn(new byte[]{1,2,3});

        mockMvc.perform(get("/api/expenses/100/export.pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"expense-100.pdf\""))
                .andExpect(content().bytes(new byte[]{1,2,3}));
    }

    @Test @DisplayName("GET /api/expenses/{id}/export.pdf -> 404 when expense not found")
    void export_pdf_not_found() throws Exception {
        when(expenses.get(404L)).thenReturn(null);

        mockMvc.perform(get("/api/expenses/404/export.pdf"))
                .andExpect(status().isNotFound());
    }

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
                ExpenseType.EQUAL, "Dinner",
                ExpenseStatus.OPEN, null
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
                ExpenseType.EQUAL, "Dinner+Drinks",
                ExpenseStatus.OPEN, null
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

    @Test @DisplayName("GET /api/expenses/{expenseId}/items -> list with precise THB conversion per item")
    void items_list_with_conversion() throws Exception {
        when(expenseRepository.findById(100L)).thenReturn(Optional.of(e));
        when(itemService.listByExpense(100L)).thenReturn(List.of(itemUsd, itemJpy, itemThb));
        Map<String, BigDecimal> rates = Map.of("USD", new BigDecimal("36.25"), "JPY", new BigDecimal("0.245"), "THB", BigDecimal.ONE);
        when(fx.getRatesToThb(e)).thenReturn(rates);
        when(fx.toThb("USD", new BigDecimal("10.00"), rates)).thenReturn(new BigDecimal("362.50"));
        when(fx.toThb("JPY", new BigDecimal("1000"), rates)).thenReturn(new BigDecimal("245.00"));
        when(fx.toThb("THB", new BigDecimal("50.00"), rates)).thenReturn(new BigDecimal("50.00"));

        mockMvc.perform(get("/api/expenses/100/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].currency").value("USD"))
                .andExpect(jsonPath("$[0].amountThb").value(362.50))
                .andExpect(jsonPath("$[1].currency").value("JPY"))
                .andExpect(jsonPath("$[1].amountThb").value(245.00))
                .andExpect(jsonPath("$[2].currency").value("THB"))
                .andExpect(jsonPath("$[2].amountThb").value(50.00));
    }

    @Test @DisplayName("GET /api/expenses/{expenseId}/items/{itemId} -> get one with THB")
    void item_get_with_conversion() throws Exception {
        when(expenseRepository.findById(100L)).thenReturn(Optional.of(e));
        when(itemService.getInExpense(100L, 1001L)).thenReturn(itemUsd);
        Map<String, BigDecimal> rates = Map.of("USD", new BigDecimal("36.25"));
        when(fx.getRatesToThb(e)).thenReturn(rates);
        when(fx.toThb("USD", new BigDecimal("10.00"), rates)).thenReturn(new BigDecimal("362.50"));

        mockMvc.perform(get("/api/expenses/100/items/1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1001))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.amountThb").value(362.50));
    }

    @Test @DisplayName("POST /api/expenses/{expenseId}/items -> create with params and THB in response")
    void item_create_and_thb() throws Exception {
        when(expenseRepository.findById(100L)).thenReturn(Optional.of(e));
        ExpenseItem created = new ExpenseItem();
        created.setId(2001L);
        created.setExpense(e);
        created.setName("Pasta");
        created.setAmount(new BigDecimal("12.34"));
        created.setCurrency("USD");

        when(itemService.create(100L, "Pasta", new BigDecimal("12.34"), "USD")).thenReturn(created);
        Map<String, BigDecimal> rates = Map.of("USD", new BigDecimal("36.25"));
        when(fx.getRatesToThb(e)).thenReturn(rates);
        when(fx.toThb("USD", new BigDecimal("12.34"), rates)).thenReturn(new BigDecimal("447.33"));

        mockMvc.perform(post("/api/expenses/100/items")
                        .param("name", "Pasta")
                        .param("amount", "12.34")
                        .param("currency", "USD"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2001))
                .andExpect(jsonPath("$.amount").value(12.34))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.amountThb").value(447.33));
    }

    @Test @DisplayName("PUT /api/expenses/{expenseId}/items/{itemId} -> update with params and THB in response")
    void item_update_and_thb() throws Exception {
        when(expenseRepository.findById(100L)).thenReturn(Optional.of(e));
        ExpenseItem updated = new ExpenseItem();
        updated.setId(1003L);
        updated.setExpense(e);
        updated.setName("Water+Ice");
        updated.setAmount(new BigDecimal("60.00"));
        updated.setCurrency("THB");

        when(itemService.updateInExpense(100L, 1003L, "Water+Ice", new BigDecimal("60.00"), "THB")).thenReturn(updated);
        Map<String, BigDecimal> rates = Map.of("THB", BigDecimal.ONE);
        when(fx.getRatesToThb(e)).thenReturn(rates);
        when(fx.toThb("THB", new BigDecimal("60.00"), rates)).thenReturn(new BigDecimal("60.00"));

        mockMvc.perform(put("/api/expenses/100/items/1003")
                        .param("name", "Water+Ice")
                        .param("amount", "60.00")
                        .param("currency", "THB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1003))
                .andExpect(jsonPath("$.name").value("Water+Ice"))
                .andExpect(jsonPath("$.amountThb").value(60.00));
    }

    @Test @DisplayName("GET /api/expenses/{expenseId}/items/total -> sum all items THB across currencies")
    void items_total_endpoint() throws Exception {
        when(expenseRepository.findById(100L)).thenReturn(Optional.of(e));
        when(itemService.listByExpense(100L)).thenReturn(List.of(itemUsd, itemJpy, itemThb));
        Map<String, BigDecimal> rates = Map.of("USD", new BigDecimal("36.25"), "JPY", new BigDecimal("0.245"), "THB", BigDecimal.ONE);
        when(fx.getRatesToThb(e)).thenReturn(rates);
        when(fx.toThb("USD", new BigDecimal("10.00"), rates)).thenReturn(new BigDecimal("362.50"));
        when(fx.toThb("JPY", new BigDecimal("1000"), rates)).thenReturn(new BigDecimal("245.00"));
        when(fx.toThb("THB", new BigDecimal("50.00"), rates)).thenReturn(new BigDecimal("50.00"));

        mockMvc.perform(get("/api/expenses/100/items/total"))
                .andExpect(status().isOk())
                .andExpect(content().string("657.50"));
    }

    @Test @DisplayName("GET /api/expenses/{expenseId}/items -> 403 when cannot view items")
    void items_list_forbidden() throws Exception {
        when(perm.canViewExpense(100L)).thenReturn(false);
        mockMvc.perform(get("/api/expenses/100/items"))
                .andExpect(status().isForbidden());
    }

    @Test @DisplayName("GET /api/expenses/{expenseId}/items/{itemId} -> 403 when cannot view item")
    void item_get_forbidden() throws Exception {
        when(perm.canViewExpenseItem(100L, 1001L)).thenReturn(false);
        mockMvc.perform(get("/api/expenses/100/items/1001"))
                .andExpect(status().isForbidden());
    }

    @Test @DisplayName("POST /api/expenses/{expenseId}/items -> 400 when missing name/amount")
    void item_create_bad_request() throws Exception {
        mockMvc.perform(post("/api/expenses/100/items")
                        .param("currency", "THB"))
                .andExpect(status().isBadRequest());
    }

    @Test @DisplayName("PUT /api/expenses/{expenseId}/items/{itemId} -> 400 when missing name/amount")
    void item_update_bad_request() throws Exception {
        mockMvc.perform(put("/api/expenses/100/items/1003"))
                .andExpect(status().isBadRequest());
    }

    @Test @DisplayName("DELETE /api/expenses/{expenseId}/items/{itemId} -> 204 when allowed")
    void item_delete_ok() throws Exception {
        doNothing().when(itemService).deleteInExpense(100L, 1001L);
        mockMvc.perform(delete("/api/expenses/100/items/1001"))
                .andExpect(status().isNoContent());
    }

    @Test @DisplayName("DELETE /api/expenses/{expenseId}/items/{itemId} -> 403 when cannot manage")
    void item_delete_forbidden() throws Exception {
        when(perm.canManageExpenseItem(100L, 1001L)).thenReturn(false);
        mockMvc.perform(delete("/api/expenses/100/items/1001"))
                .andExpect(status().isForbidden());
    }


    @Test @DisplayName("Shares: validate percent range and either value or percent provided")
    void shares_validation() throws Exception {
        mockMvc.perform(post("/api/expenses/100/items/1001/shares")
                        .param("participantUserId", "30"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/expenses/100/items/1001/shares")
                        .param("participantUserId", "30")
                        .param("sharePercent", "-1"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/expenses/100/items/1001/shares")
                        .param("participantUserId", "30")
                        .param("sharePercent", "101"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/expenses/100/items/1001/shares/9002"))
                .andExpect(status().isBadRequest());
    }

    @Test @DisplayName("Permission: outsider cannot view item even if present in share when perm denies")
    void outsider_in_share_but_perm_denies() throws Exception {
        when(perm.canViewExpenseItem(100L, 1001L)).thenReturn(false);
        mockMvc.perform(get("/api/expenses/100/items/1001/shares"))
                .andExpect(status().isForbidden());
    }

    @Test @DisplayName("Permission: outsider can view item if perm allows based on being a participant")
    void outsider_in_share_and_perm_allows() throws Exception {
        when(perm.canViewExpenseItem(100L, 1001L)).thenReturn(true);
        ExpenseItemShare s1 = new ExpenseItemShare();
        s1.setId(9100L);
        s1.setExpenseItem(itemUsd);
        User outsider = new User(); outsider.setId(77L); outsider.setUserName("Outsider");
        s1.setParticipant(outsider);
        s1.setShareOriginalValue(new BigDecimal("1.00"));
        s1.setShareValue(new BigDecimal("36.25"));
        when(shareService.listByItemInExpense(100L, 1001L)).thenReturn(List.of(s1));

        mockMvc.perform(get("/api/expenses/100/items/1001/shares"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(9100))
                .andExpect(jsonPath("$[0].shareValue").value(36.25));
    }

    @Test @DisplayName("FX: item list with EUR precise conversion and total checks")
    void items_list_with_eur() throws Exception {
        when(expenseRepository.findById(100L)).thenReturn(Optional.of(e));
        ExpenseItem eur = new ExpenseItem();
        eur.setId(2002L);
        eur.setExpense(e);
        eur.setName("Olive Oil");
        eur.setAmount(new BigDecimal("1.23"));
        eur.setCurrency("EUR");

        when(itemService.listByExpense(100L)).thenReturn(List.of(itemUsd, itemJpy, itemThb, eur));
        Map<String, BigDecimal> rates = Map.of("USD", new BigDecimal("36.25"), "JPY", new BigDecimal("0.245"), "THB", BigDecimal.ONE, "EUR", new BigDecimal("39.90"));
        when(fx.getRatesToThb(e)).thenReturn(rates);
        when(fx.toThb("USD", new BigDecimal("10.00"), rates)).thenReturn(new BigDecimal("362.50"));
        when(fx.toThb("JPY", new BigDecimal("1000"), rates)).thenReturn(new BigDecimal("245.00"));
        when(fx.toThb("THB", new BigDecimal("50.00"), rates)).thenReturn(new BigDecimal("50.00"));
        when(fx.toThb("EUR", new BigDecimal("1.23"), rates)).thenReturn(new BigDecimal("49.08"));

        mockMvc.perform(get("/api/expenses/100/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[3].currency").value("EUR"))
                .andExpect(jsonPath("$[3].amountThb").value(49.08));

        when(itemService.listByExpense(100L)).thenReturn(List.of(itemUsd, itemJpy, itemThb, eur));
        mockMvc.perform(get("/api/expenses/100/items/total"))
                .andExpect(status().isOk())
                .andExpect(content().string("706.58"));
    }

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
