package com.smartsplit.smartsplitback.controller;

import com.smartsplit.smartsplitback.model.dto.ExpenseItemShareDto;
import com.smartsplit.smartsplitback.service.ExpenseItemShareService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/expenses/{expenseId}/shares")
public class ExpenseSharesQueryController {

    private final ExpenseItemShareService shares;

    public ExpenseSharesQueryController(ExpenseItemShareService shares) {
        this.shares = shares;
    }

    @PreAuthorize("@perm.canViewExpense(#expenseId)")
    @GetMapping
    public List<ExpenseItemShareDto> listByExpense(@PathVariable Long expenseId) {
        return shares.listByExpense(expenseId).stream()
                .map(ExpenseItemShareDto::fromEntity)
                .toList();
    }
}
