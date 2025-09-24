
package com.smartsplit.smartsplitback.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartsplit.smartsplitback.model.Expense;
import com.smartsplit.smartsplitback.model.ExpenseItem;
import com.smartsplit.smartsplitback.security.JwtAuthFilter;
import com.smartsplit.smartsplitback.security.JwtService;
import com.smartsplit.smartsplitback.security.Perms;
import com.smartsplit.smartsplitback.service.ExpenseItemService;
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

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SuppressWarnings("removal")
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


    @MockitoBean JwtAuthFilter jwtAuthFilter;
    @MockitoBean JwtService jwtService;


    @MockitoBean(name = "perm", answers = Answers.RETURNS_DEFAULTS)
    Perms perm;

    private Expense parent;
    private ExpenseItem item1;
    private ExpenseItem item2;

    @BeforeEach
    void setUp() {
       
        when(perm.canViewExpense(anyLong())).thenReturn(true);
        when(perm.canViewExpenseItem(anyLong(), anyLong())).thenReturn(true);
        when(perm.canManageExpense(anyLong())).thenReturn(true);
        when(perm.canManageExpenseItem(anyLong(), anyLong())).thenReturn(true);

        parent = new Expense();
        parent.setId(100L);

        item1 = new ExpenseItem();
        item1.setId(200L);
        item1.setName("Cola");
        item1.setAmount(new BigDecimal("10.50"));
        try { item1.getClass().getMethod("setExpense", Expense.class).invoke(item1, parent); } catch (Exception ignored) {}

        item2 = new ExpenseItem();
        item2.setId(201L);
        item2.setName("Burger");
        item2.setAmount(new BigDecimal("45.00"));
        try { item2.getClass().getMethod("setExpense", Expense.class).invoke(item2, parent); } catch (Exception ignored) {}
    }

    // ---------- OK cases เดิม ----------

    @Test
    @DisplayName("GET /api/expenses/{expenseId}/items -> list items")
    void list_items() throws Exception {
        when(items.listByExpense(100L)).thenReturn(List.of(item1, item2));

        mockMvc.perform(get("/api/expenses/100/items"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(200))
                .andExpect(jsonPath("$[0].expenseId").value(100))
                .andExpect(jsonPath("$[0].name").value("Cola"))
                .andExpect(jsonPath("$[0].amount").value(10.50))
                .andExpect(jsonPath("$[1].id").value(201))
                .andExpect(jsonPath("$[1].name").value("Burger"))
                .andExpect(jsonPath("$[1].amount").value(45.00));
    }

    @Test
    @DisplayName("GET /api/expenses/{expenseId}/items/{itemId} -> get one")
    void get_one() throws Exception {
        when(items.getInExpense(100L, 200L)).thenReturn(item1);

        mockMvc.perform(get("/api/expenses/100/items/200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(200))
                .andExpect(jsonPath("$.expenseId").value(100))
                .andExpect(jsonPath("$.name").value("Cola"))
                .andExpect(jsonPath("$.amount").value(10.50));
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
    @DisplayName("POST /api/expenses/{expenseId}/items -> create (form params)")
    void create_item() throws Exception {
        when(items.create(100L, "Water", new BigDecimal("12.34")))
                .thenReturn(item1);

        mockMvc.perform(
                        post("/api/expenses/100/items")
                                .param("name", "Water")
                                .param("amount", "12.34")
                )
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(200))
                .andExpect(jsonPath("$.expenseId").value(100))
                .andExpect(jsonPath("$.name").value("Cola"))
                .andExpect(jsonPath("$.amount").value(10.50));
    }

    @Test
    @DisplayName("PUT /api/expenses/{expenseId}/items/{itemId} -> update (form params)")
    void update_item() throws Exception {
        var updated = new ExpenseItem();
        updated.setId(200L);
        updated.setName("Cola (L)");
        updated.setAmount(new BigDecimal("15.00"));
        try { updated.getClass().getMethod("setExpense", Expense.class).invoke(updated, parent); } catch (Exception ignored) {}

        when(items.updateInExpense(100L, 200L, "Cola (L)", new BigDecimal("15.00")))
                .thenReturn(updated);

        mockMvc.perform(
                        put("/api/expenses/100/items/200")
                                .param("name", "Cola (L)")
                                .param("amount", "15.00")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(200))
                .andExpect(jsonPath("$.expenseId").value(100))
                .andExpect(jsonPath("$.name").value("Cola (L)"))
                .andExpect(jsonPath("$.amount").value(15.00));
    }

    @Test
    @DisplayName("DELETE /api/expenses/{expenseId}/items/{itemId} -> 204")
    void delete_item() throws Exception {
        doNothing().when(items).deleteInExpense(100L, 200L);

        mockMvc.perform(delete("/api/expenses/100/items/200"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("GET /api/expenses/{expenseId}/items/total -> sumItems")
    void total_items_amount() throws Exception {
        when(items.sumItems(100L)).thenReturn(new BigDecimal("55.50"));

        mockMvc.perform(get("/api/expenses/100/items/total"))
                .andExpect(status().isOk())
                .andExpect(content().string("55.50"));
    }

    // ---------- Permission (403) เพิ่มเติม ----------

    @Test
    @DisplayName("GET items -> 403 เมื่อไม่มีสิทธิ์ดู expense")
    void list_items_forbidden_whenCannotViewExpense() throws Exception {
        when(perm.canViewExpense(100L)).thenReturn(false);

        mockMvc.perform(get("/api/expenses/100/items"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET one item -> 403 เมื่อไม่มีสิทธิ์ดู item")
    void get_one_forbidden_whenCannotViewItem() throws Exception {
        when(perm.canViewExpenseItem(100L, 200L)).thenReturn(false);

        mockMvc.perform(get("/api/expenses/100/items/200"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST create item -> 403 เมื่อไม่มีสิทธิ์จัดการ expense")
    void create_item_forbidden_whenCannotManageExpense() throws Exception {
        when(perm.canManageExpense(100L)).thenReturn(false);

        mockMvc.perform(
                post("/api/expenses/100/items")
                        .param("name", "Water")
                        .param("amount", "12.34")
        ).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT update item -> 403 เมื่อไม่มีสิทธิ์จัดการ item")
    void update_item_forbidden_whenCannotManageItem() throws Exception {
        when(perm.canManageExpenseItem(100L, 200L)).thenReturn(false);

        mockMvc.perform(
                put("/api/expenses/100/items/200")
                        .param("name", "Cola (L)")
                        .param("amount", "15.00")
        ).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE item -> 403 เมื่อไม่มีสิทธิ์จัดการ item")
    void delete_item_forbidden_whenCannotManageItem() throws Exception {
        when(perm.canManageExpenseItem(100L, 200L)).thenReturn(false);

        mockMvc.perform(delete("/api/expenses/100/items/200"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET total -> 403 เมื่อไม่มีสิทธิ์ดู expense")
    void total_items_amount_forbidden_whenCannotViewExpense() throws Exception {
        when(perm.canViewExpense(100L)).thenReturn(false);

        mockMvc.perform(get("/api/expenses/100/items/total"))
                .andExpect(status().isForbidden());
    }

    // ---------- Param/Validation (400) เพิ่มเติม ----------
    // หมายเหตุ: ถ้าคอนโทรลเลอร์ใช้ @RequestParam แบบ required=true จะ 400 อัตโนมัติเมื่อขาดพารามิเตอร์

    @Test
    @DisplayName("POST create item -> 400 เมื่อไม่มีชื่อ name")
    void create_item_badRequest_whenMissingName() throws Exception {
        // ให้มีสิทธิ์จัดการ expense
        when(perm.canManageExpense(100L)).thenReturn(true);

        mockMvc.perform(
                post("/api/expenses/100/items")
                        .param("amount", "12.34") // ขาด name
        ).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST create item -> 400 เมื่อไม่มี amount")
    void create_item_badRequest_whenMissingAmount() throws Exception {
        when(perm.canManageExpense(100L)).thenReturn(true);

        mockMvc.perform(
                post("/api/expenses/100/items")
                        .param("name", "Water") // ขาด amount
        ).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST create item -> 400 เมื่อ amount ไม่เป็นตัวเลข")
    void create_item_badRequest_whenAmountNotNumber() throws Exception {
        when(perm.canManageExpense(100L)).thenReturn(true);

        mockMvc.perform(
                post("/api/expenses/100/items")
                        .param("name", "Water")
                        .param("amount", "abc") // parse ไม่ได้
        ).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT update item -> 400 เมื่อไม่มี name")
    void update_item_badRequest_whenMissingName() throws Exception {
        when(perm.canManageExpenseItem(100L, 200L)).thenReturn(true);

        mockMvc.perform(
                put("/api/expenses/100/items/200")
                        .param("amount", "15.00") // ขาด name
        ).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT update item -> 400 เมื่อไม่มี amount")
    void update_item_badRequest_whenMissingAmount() throws Exception {
        when(perm.canManageExpenseItem(100L, 200L)).thenReturn(true);

        mockMvc.perform(
                put("/api/expenses/100/items/200")
                        .param("name", "Cola (L)") // ขาด amount
        ).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT update item -> 400 เมื่อ amount ไม่เป็นตัวเลข")
    void update_item_badRequest_whenAmountNotNumber() throws Exception {
        when(perm.canManageExpenseItem(100L, 200L)).thenReturn(true);

        mockMvc.perform(
                put("/api/expenses/100/items/200")
                        .param("name", "Cola (L)")
                        .param("amount", "x") // parse ไม่ได้
        ).andExpect(status().isBadRequest());
    }
}
