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
        var list = items.listByExpense(expenseId);
        if (list == null) return List.of();                // กัน null list
        return list.stream().map(ExpenseItemDto::fromEntity).toList();
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

        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        if (amount == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount is required");
        }

        var created = items.create(expenseId, name, amount);
        if (created == null) {

            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create expense item");
        }
        return ExpenseItemDto.fromEntity(created);
    }

    @PreAuthorize("@perm.canManageExpenseItem(#expenseId, #itemId)")
    @PutMapping("/{itemId}")
    public ExpenseItemDto update(@PathVariable Long expenseId,
                                 @PathVariable Long itemId,
                                 @RequestParam(required = false) String name,
                                 @RequestParam(required = false) BigDecimal amount) {


        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        if (amount == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount is required");
        }

        var updated = items.updateInExpense(expenseId, itemId, name, amount);
        if (updated == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense item not found in this expense");
        }
        return ExpenseItemDto.fromEntity(updated);
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
        var sum = items.sumItems(expenseId);
        return sum == null ? BigDecimal.ZERO : sum;        // กัน null เผื่อ service ยังไม่เซ็ต
    }
}
