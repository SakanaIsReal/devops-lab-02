package com.smartsplit.smartsplitback.controller;

import com.smartsplit.smartsplitback.model.ExpenseItem;
import com.smartsplit.smartsplitback.model.dto.ExpenseItemDto;
import com.smartsplit.smartsplitback.service.ExpenseItemService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/expenses/{expenseId}/items")
public class ExpenseItemController {

    private final ExpenseItemService items;

    public ExpenseItemController(ExpenseItemService items) {
        this.items = items;
    }


    @PreAuthorize("@perm.canViewExpense(#expenseId)")
    @GetMapping
    public List<ExpenseItemDto> list(@PathVariable Long expenseId) {
        return items.listByExpense(expenseId).stream()
                .map(ExpenseItemDto::fromEntity)
                .toList();
    }


    @PreAuthorize("@perm.canViewExpenseItem(#expenseId, #itemId)")
    @GetMapping("/{itemId}")
    public ExpenseItemDto get(@PathVariable Long expenseId, @PathVariable Long itemId) {
        ExpenseItem it = items.getInExpense(expenseId, itemId);
        if (it == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense item not found in this expense");
        return ExpenseItemDto.fromEntity(it);
    }


    @PreAuthorize("@perm.canManageExpense(#expenseId)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseItemDto create(@PathVariable Long expenseId,
                                 @RequestParam String name,
                                 @RequestParam BigDecimal amount) {
        return ExpenseItemDto.fromEntity(items.create(expenseId, name, amount));
    }


    @PreAuthorize("@perm.canManageExpenseItem(#expenseId, #itemId)")
    @PutMapping("/{itemId}")
    public ExpenseItemDto update(@PathVariable Long expenseId,
                                 @PathVariable Long itemId,
                                 @RequestParam(required = false) String name,
                                 @RequestParam(required = false) BigDecimal amount) {
        return ExpenseItemDto.fromEntity(items.updateInExpense(expenseId, itemId, name, amount));
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
        return items.sumItems(expenseId);
    }
}
