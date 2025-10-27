package com.smartsplit.smartsplitback.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartsplit.smartsplitback.model.Expense;
import com.smartsplit.smartsplitback.model.ExpenseItem;
import com.smartsplit.smartsplitback.security.JwtAuthFilter;
import com.smartsplit.smartsplitback.security.JwtService;
import com.smartsplit.smartsplitback.security.Perms;
import com.smartsplit.smartsplitback.service.ExchangeRateService;
import com.smartsplit.smartsplitback.service.ExpenseItemService;
import com.smartsplit.smartsplitback.repository.ExpenseRepository;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExpenseItemController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
        ExpenseItemControllerTest.MethodSecurityTestConfig.class,
        ExpenseItemControllerTest.MethodSecurityExceptionAdvice.class
})
class ExpenseItemControllerTest {

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

    @MockitoBean ExpenseItemService items;
    @MockitoBean ExpenseRepository expenses;
    @MockitoBean ExchangeRateService fx;

    @MockitoBean JwtAuthFilter jwtAuthFilter;
    @MockitoBean JwtService jwtService;

    @MockitoBean(name = "perm", answers = Answers.RETURNS_DEFAULTS)
    Perms perm;

    private Expense parent;
    private ExpenseItem itemThb;
    private ExpenseItem itemUsd;

    @BeforeEach
    void setUp() throws Exception {
        when(perm.canViewExpense(anyLong())).thenReturn(true);
        when(perm.canViewExpenseItem(anyLong(), anyLong())).thenReturn(true);
        when(perm.canManageExpense(anyLong())).thenReturn(true);
        when(perm.canManageExpenseItem(anyLong(), anyLong())).thenReturn(true);

        parent = new Expense();
        parent.setId(100L);

        itemThb = new ExpenseItem();
        itemThb.setId(200L);
        itemThb.setName("Cola");
        itemThb.setAmount(new BigDecimal("10.50"));
        itemThb.setCurrency("THB");
        ExpenseItem.class.getMethod("setExpense", Expense.class).invoke(itemThb, parent);

        itemUsd = new ExpenseItem();
        itemUsd.setId(201L);
        itemUsd.setName("Burger");
        itemUsd.setAmount(new BigDecimal("45.00"));
        itemUsd.setCurrency("USD");
        ExpenseItem.class.getMethod("setExpense", Expense.class).invoke(itemUsd, parent);

        when(expenses.findById(100L)).thenReturn(java.util.Optional.of(parent));
        when(fx.getRatesToThb(parent)).thenReturn(Map.of("THB", BigDecimal.ONE, "USD", new BigDecimal("36.00")));
        when(fx.toThb(eq("THB"), any(BigDecimal.class), anyMap())).thenAnswer(inv -> inv.getArgument(1, BigDecimal.class));
        when(fx.toThb(eq("USD"), eq(new BigDecimal("45.00")), anyMap())).thenReturn(new BigDecimal("1620.00"));
        when(fx.toThb(eq("USD"), eq(new BigDecimal("15.00")), anyMap())).thenReturn(new BigDecimal("540.00"));
        when(fx.toThb(eq("USD"), eq(new BigDecimal("2.50")), anyMap())).thenReturn(new BigDecimal("90.00"));
        when(fx.toThb(eq("USD"), eq(new BigDecimal("1.00")), anyMap())).thenReturn(new BigDecimal("36.00"));
        when(fx.toThb(eq("USD"), eq(new BigDecimal("0.01")), anyMap())).thenReturn(new BigDecimal("0.36"));
        when(fx.toThb(eq("USD"), eq(new BigDecimal("9999999999.99")), anyMap())).thenReturn(new BigDecimal("359999999999.64"));
    }

    @Test
    @DisplayName("GET /api/expenses/{expenseId}/items -> list items with amountThb conversion")
    void list_items() throws Exception {
        when(items.listByExpense(100L)).thenReturn(List.of(itemThb, itemUsd));

        mockMvc.perform(get("/api/expenses/100/items"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(200))
                .andExpect(jsonPath("$[0].expenseId").value(100))
                .andExpect(jsonPath("$[0].name").value("Cola"))
                .andExpect(jsonPath("$[0].amount").value(10.50))
                .andExpect(jsonPath("$[0].currency").value("THB"))
                .andExpect(jsonPath("$[0].amountThb").value(10.50))
                .andExpect(jsonPath("$[1].id").value(201))
                .andExpect(jsonPath("$[1].name").value("Burger"))
                .andExpect(jsonPath("$[1].amount").value(45.00))
                .andExpect(jsonPath("$[1].currency").value("USD"))
                .andExpect(jsonPath("$[1].amountThb").value(1620.00));
    }

    @Test
    @DisplayName("GET /api/expenses/{expenseId}/items -> 404 when expense not found")
    void list_items_expense_not_found() throws Exception {
        when(expenses.findById(404L)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/expenses/404/items"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/expenses/{expenseId}/items/{itemId} -> get one with amountThb")
    void get_one() throws Exception {
        when(items.getInExpense(100L, 200L)).thenReturn(itemThb);

        mockMvc.perform(get("/api/expenses/100/items/200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(200))
                .andExpect(jsonPath("$.expenseId").value(100))
                .andExpect(jsonPath("$.name").value("Cola"))
                .andExpect(jsonPath("$.amount").value(10.50))
                .andExpect(jsonPath("$.currency").value("THB"))
                .andExpect(jsonPath("$.amountThb").value(10.50));
    }

    @Test
    @DisplayName("GET /api/expenses/{expenseId}/items/{itemId} -> 404 when not found in that expense")
    void get_one_not_found() throws Exception {
        when(items.getInExpense(100L, 999L)).thenReturn(null);

        mockMvc.perform(get("/api/expenses/100/items/999"))
                .andExpect(status().isNotFound())
                .andExpect(status().reason("Expense item not found in this expense"));
    }

    @Test
    @DisplayName("GET one -> 404 when expense not found")
    void get_one_expense_not_found() throws Exception {
        when(expenses.findById(404L)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/expenses/404/items/200"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/expenses/{expenseId}/items -> create default THB and amountThb returned")
    void create_item_default_thb() throws Exception {
        ExpenseItem created = new ExpenseItem();
        created.setId(300L);
        created.setName("Water");
        created.setAmount(new BigDecimal("12.34"));
        created.setCurrency("THB");
        ExpenseItem.class.getMethod("setExpense", Expense.class).invoke(created, parent);

        when(items.create(100L, "Water", new BigDecimal("12.34"), "THB")).thenReturn(created);

        mockMvc.perform(post("/api/expenses/100/items")
                        .param("name", "Water")
                        .param("amount", "12.34"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(300))
                .andExpect(jsonPath("$.expenseId").value(100))
                .andExpect(jsonPath("$.name").value("Water"))
                .andExpect(jsonPath("$.currency").value("THB"))
                .andExpect(jsonPath("$.amount").value(12.34))
                .andExpect(jsonPath("$.amountThb").value(12.34));
    }

    @Test
    @DisplayName("POST /api/expenses/{expenseId}/items -> create with USD and amountThb conversion")
    void create_item_usd() throws Exception {
        ExpenseItem created = new ExpenseItem();
        created.setId(301L);
        created.setName("Fries");
        created.setAmount(new BigDecimal("2.50"));
        created.setCurrency("USD");
        ExpenseItem.class.getMethod("setExpense", Expense.class).invoke(created, parent);

        when(items.create(100L, "Fries", new BigDecimal("2.50"), "USD")).thenReturn(created);

        mockMvc.perform(post("/api/expenses/100/items")
                        .param("name", "Fries")
                        .param("amount", "2.50")
                        .param("currency", "USD"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(301))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.amount").value(2.50))
                .andExpect(jsonPath("$.amountThb").value(90.00));
    }

    @Test
    @DisplayName("POST create -> 404 when expense not found")
    void create_item_expense_not_found() throws Exception {
        when(expenses.findById(999L)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(post("/api/expenses/999/items")
                        .param("name", "X")
                        .param("amount", "1.00"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /api/expenses/{expenseId}/items/{itemId} -> update with USD and converted amountThb")
    void update_item() throws Exception {
        ExpenseItem updated = new ExpenseItem();
        updated.setId(200L);
        updated.setName("Cola (L)");
        updated.setAmount(new BigDecimal("15.00"));
        updated.setCurrency("USD");
        ExpenseItem.class.getMethod("setExpense", Expense.class).invoke(updated, parent);

        when(items.updateInExpense(100L, 200L, "Cola (L)", new BigDecimal("15.00"), "USD")).thenReturn(updated);

        mockMvc.perform(put("/api/expenses/100/items/200")
                        .param("name", "Cola (L)")
                        .param("amount", "15.00")
                        .param("currency", "USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(200))
                .andExpect(jsonPath("$.expenseId").value(100))
                .andExpect(jsonPath("$.name").value("Cola (L)"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.amount").value(15.00))
                .andExpect(jsonPath("$.amountThb").value(540.00));
    }

    @Test
    @DisplayName("PUT update -> 404 when expense not found")
    void update_item_expense_not_found() throws Exception {
        when(expenses.findById(404L)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(put("/api/expenses/404/items/200")
                        .param("name", "X")
                        .param("amount", "1.00"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT update -> 404 when item not in expense")
    void update_item_not_found_in_expense() throws Exception {
        when(items.updateInExpense(100L, 999L, "X", new BigDecimal("1.00"), null)).thenReturn(null);

        mockMvc.perform(put("/api/expenses/100/items/999")
                        .param("name", "X")
                        .param("amount", "1.00"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/expenses/{expenseId}/items/{itemId} -> 204")
    void delete_item() throws Exception {
        doNothing().when(items).deleteInExpense(100L, 200L);

        mockMvc.perform(delete("/api/expenses/100/items/200"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE -> 403 when cannot manage item")
    void delete_item_forbidden() throws Exception {
        when(perm.canManageExpenseItem(100L, 200L)).thenReturn(false);

        mockMvc.perform(delete("/api/expenses/100/items/200"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/expenses/{expenseId}/items/total -> sum THB across items")
    void total_items_amount() throws Exception {
        ExpenseItem thb = new ExpenseItem();
        thb.setId(401L);
        thb.setName("Rice");
        thb.setAmount(new BigDecimal("50.00"));
        thb.setCurrency("THB");
        ExpenseItem.class.getMethod("setExpense", Expense.class).invoke(thb, parent);

        ExpenseItem usd1 = new ExpenseItem();
        usd1.setId(402L);
        usd1.setName("Soda");
        usd1.setAmount(new BigDecimal("2.50"));
        usd1.setCurrency("USD");
        ExpenseItem.class.getMethod("setExpense", Expense.class).invoke(usd1, parent);

        ExpenseItem usd2 = new ExpenseItem();
        usd2.setId(403L);
        usd2.setName("Tips");
        usd2.setAmount(new BigDecimal("1.00"));
        usd2.setCurrency("USD");
        ExpenseItem.class.getMethod("setExpense", Expense.class).invoke(usd2, parent);

        when(items.listByExpense(100L)).thenReturn(List.of(thb, usd1, usd2));

        mockMvc.perform(get("/api/expenses/100/items/total"))
                .andExpect(status().isOk())
                .andExpect(content().string("176.00"));
    }

    @Test
    @DisplayName("GET /api/expenses/{expenseId}/items/total -> 0.00 when no items")
    void total_items_amount_zero() throws Exception {
        when(items.listByExpense(100L)).thenReturn(List.of());

        mockMvc.perform(get("/api/expenses/100/items/total"))
                .andExpect(status().isOk())
                .andExpect(content().string("0.00"));
    }

    @Test
    @DisplayName("GET total -> 404 when expense not found")
    void total_items_expense_not_found() throws Exception {
        when(expenses.findById(999L)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/expenses/999/items/total"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET items -> 403 when cannot view expense")
    void list_items_forbidden_whenCannotViewExpense() throws Exception {
        when(perm.canViewExpense(100L)).thenReturn(false);

        mockMvc.perform(get("/api/expenses/100/items"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET one item -> 403 when cannot view item")
    void get_one_forbidden_whenCannotViewItem() throws Exception {
        when(perm.canViewExpenseItem(100L, 200L)).thenReturn(false);

        mockMvc.perform(get("/api/expenses/100/items/200"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST create -> 403 when cannot manage expense")
    void create_item_forbidden_whenCannotManageExpense() throws Exception {
        when(perm.canManageExpense(100L)).thenReturn(false);

        mockMvc.perform(post("/api/expenses/100/items")
                        .param("name", "Water")
                        .param("amount", "12.34"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT update -> 403 when cannot manage item")
    void update_item_forbidden_whenCannotManageItem() throws Exception {
        when(perm.canManageExpenseItem(100L, 200L)).thenReturn(false);

        mockMvc.perform(put("/api/expenses/100/items/200")
                        .param("name", "Cola (L)")
                        .param("amount", "15.00"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET total -> 403 when cannot view expense")
    void total_items_amount_forbidden_whenCannotViewExpense() throws Exception {
        when(perm.canViewExpense(100L)).thenReturn(false);

        mockMvc.perform(get("/api/expenses/100/items/total"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST create -> 400 when missing name")
    void create_item_badRequest_whenMissingName() throws Exception {
        mockMvc.perform(post("/api/expenses/100/items")
                        .param("amount", "12.34"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST create -> 400 when missing amount")
    void create_item_badRequest_whenMissingAmount() throws Exception {
        mockMvc.perform(post("/api/expenses/100/items")
                        .param("name", "Water"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST create -> 400 when amount not a number")
    void create_item_badRequest_whenAmountNotNumber() throws Exception {
        mockMvc.perform(post("/api/expenses/100/items")
                        .param("name", "Water")
                        .param("amount", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT update -> 400 when missing name")
    void update_item_badRequest_whenMissingName() throws Exception {
        mockMvc.perform(put("/api/expenses/100/items/200")
                        .param("amount", "15.00"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT update -> 400 when missing amount")
    void update_item_badRequest_whenMissingAmount() throws Exception {
        mockMvc.perform(put("/api/expenses/100/items/200")
                        .param("name", "Cola (L)"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT update -> 400 when amount not a number")
    void update_item_badRequest_whenAmountNotNumber() throws Exception {
        mockMvc.perform(put("/api/expenses/100/items/200")
                        .param("name", "Cola (L)")
                        .param("amount", "x"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("ISP amounts: 0, 0.01, large, and currency default/override")
    void isp_amount_boundaries_and_currency() throws Exception {
        ExpenseItem zero = new ExpenseItem();
        zero.setId(501L);
        zero.setName("Z");
        zero.setAmount(new BigDecimal("0.00"));
        zero.setCurrency("THB");
        ExpenseItem.class.getMethod("setExpense", Expense.class).invoke(zero, parent);
        when(items.create(100L, "Z", new BigDecimal("0.00"), "THB")).thenReturn(zero);

        mockMvc.perform(post("/api/expenses/100/items")
                        .param("name", "Z")
                        .param("amount", "0"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(0.0))
                .andExpect(jsonPath("$.amountThb").value(0.0))
                .andExpect(jsonPath("$.currency").value("THB"));

        ExpenseItem cent = new ExpenseItem();
        cent.setId(502L);
        cent.setName("C");
        cent.setAmount(new BigDecimal("0.01"));
        cent.setCurrency("USD");
        ExpenseItem.class.getMethod("setExpense", Expense.class).invoke(cent, parent);
        when(items.create(100L, "C", new BigDecimal("0.01"), "USD")).thenReturn(cent);

        mockMvc.perform(post("/api/expenses/100/items")
                        .param("name", "C")
                        .param("amount", "0.01")
                        .param("currency", "USD"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(0.01))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.amountThb").value(0.36));

        ExpenseItem big = new ExpenseItem();
        big.setId(503L);
        big.setName("B");
        big.setAmount(new BigDecimal("9999999999.99"));
        big.setCurrency("USD");
        ExpenseItem.class.getMethod("setExpense", Expense.class).invoke(big, parent);
        when(items.create(100L, "B", new BigDecimal("9999999999.99"), "USD")).thenReturn(big);

        mockMvc.perform(post("/api/expenses/100/items")
                        .param("name", "B")
                        .param("amount", "9999999999.99")
                        .param("currency", "USD"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(9999999999.99))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.amountThb").value(359999999999.64));
    }
}
