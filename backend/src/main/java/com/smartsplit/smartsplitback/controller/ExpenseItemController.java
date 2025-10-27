package com.smartsplit.smartsplitback.controller;

import com.smartsplit.smartsplitback.model.Expense;
import com.smartsplit.smartsplitback.model.ExpenseItem;
import com.smartsplit.smartsplitback.model.dto.ExpenseItemDto;
import com.smartsplit.smartsplitback.repository.ExpenseRepository;
import com.smartsplit.smartsplitback.service.ExchangeRateService;
import com.smartsplit.smartsplitback.service.ExpenseItemService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/expenses/{expenseId}/items")
public class ExpenseItemController {

    private final ExpenseItemService items;
    private final ExpenseRepository expenses;
    private final ExchangeRateService fx;

    public ExpenseItemController(ExpenseItemService items,
                                 ExpenseRepository expenses,
                                 ExchangeRateService fx) {
        this.items = items;
        this.expenses = expenses;
        this.fx = fx;
    }

    private Expense mustExpense(Long expenseId) {
        return expenses.findById(expenseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found"));
    }

    private static String upper(String ccy) {
        return ccy == null ? null : ccy.toUpperCase(Locale.ROOT);
    }

    @PreAuthorize("@perm.canViewExpense(#expenseId)")
    @GetMapping
    public List<ExpenseItemDto> list(@PathVariable Long expenseId) {
        Expense e = mustExpense(expenseId);
        Map<String, BigDecimal> rates = fx.getRatesToThb(e);
        var list = items.listByExpense(expenseId);
        if (list == null) return List.of();
        return list.stream()
                .map(it -> {
                    BigDecimal thb = fx.toThb(it.getCurrency(), it.getAmount(), rates)
                            .setScale(2, RoundingMode.HALF_UP);
                    return ExpenseItemDto.fromEntity(it, thb);
                })
                .toList();
    }

    @PreAuthorize("@perm.canViewExpenseItem(#expenseId, #itemId)")
    @GetMapping("/{itemId}")
    public ExpenseItemDto get(@PathVariable Long expenseId, @PathVariable Long itemId) {
        Expense e = mustExpense(expenseId);
        Map<String, BigDecimal> rates = fx.getRatesToThb(e);
        ExpenseItem it = items.getInExpense(expenseId, itemId);
        if (it == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense item not found in this expense");
        BigDecimal thb = fx.toThb(it.getCurrency(), it.getAmount(), rates)
                .setScale(2, RoundingMode.HALF_UP);
        return ExpenseItemDto.fromEntity(it, thb);
    }

    @PreAuthorize("@perm.canManageExpense(#expenseId)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseItemDto create(@PathVariable Long expenseId,
                                 @RequestParam String name,
                                 @RequestParam BigDecimal amount,
                                 @RequestParam(defaultValue = "THB") String currency) {

        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        if (amount == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount is required");
        }

        Expense e = mustExpense(expenseId);
        Map<String, BigDecimal> rates = fx.getRatesToThb(e);

        BigDecimal normalizedAmount = amount.setScale(2, RoundingMode.HALF_UP);
        String ccy = upper(currency);

        var created = items.create(expenseId, name, normalizedAmount, ccy);
        if (created == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create expense item");
        }

        BigDecimal thb = fx.toThb(created.getCurrency(), created.getAmount(), rates)
                .setScale(2, RoundingMode.HALF_UP);

        return ExpenseItemDto.fromEntity(created, thb);
    }

    @PreAuthorize("@perm.canManageExpenseItem(#expenseId, #itemId)")
    @PutMapping("/{itemId}")
    public ExpenseItemDto update(@PathVariable Long expenseId,
                                 @PathVariable Long itemId,
                                 @RequestParam(required = false) String name,
                                 @RequestParam(required = false) BigDecimal amount,
                                 @RequestParam(required = false) String currency) {

        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        if (amount == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount is required");
        }

        BigDecimal normalizedAmount = amount.setScale(2, RoundingMode.HALF_UP);
        String ccy = currency == null ? null : upper(currency);

        var updated = items.updateInExpense(expenseId, itemId, name, normalizedAmount, ccy);
        if (updated == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense item not found in this expense");
        }

        Expense e = mustExpense(expenseId);
        Map<String, BigDecimal> rates = fx.getRatesToThb(e);

        BigDecimal thb = fx.toThb(updated.getCurrency(), updated.getAmount(), rates)
                .setScale(2, RoundingMode.HALF_UP);

        return ExpenseItemDto.fromEntity(updated, thb);
    }

    @PreAuthorize("@perm.canManageExpenseItem(#expenseId, #itemId)")
    @DeleteMapping("/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long expenseId, @PathVariable Long itemId) {
        items.deleteInExpense(expenseId, itemId);
    }

    @PreAuthorize("@perm.canViewExpense(#expenseId)")
    @GetMapping("/total")
    public BigDecimal total(@PathVariable Long expenseId) {
        Expense e = mustExpense(expenseId);
        Map<String, BigDecimal> rates = fx.getRatesToThb(e);

        var list = items.listByExpense(expenseId);
        if (list == null || list.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal sum = list.stream()
                .map(it -> fx.toThb(it.getCurrency(), it.getAmount(), rates)
                        .setScale(2, RoundingMode.HALF_UP)) // ปัดต่อรายการ
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return sum.setScale(2, RoundingMode.HALF_UP);
    }
}
