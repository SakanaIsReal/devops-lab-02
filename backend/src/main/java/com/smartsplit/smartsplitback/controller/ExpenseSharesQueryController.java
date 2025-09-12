package com.smartsplit.smartsplitback.controller;

import com.smartsplit.smartsplitback.model.dto.ExpenseItemShareDto;
import com.smartsplit.smartsplitback.security.Perms;
import com.smartsplit.smartsplitback.service.ExpenseItemShareService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/expenses/{expenseId}/shares")
public class ExpenseSharesQueryController {

    private final ExpenseItemShareService shares;
    private final Perms perm;

    public ExpenseSharesQueryController(ExpenseItemShareService shares, Perms perm) {
        this.shares = shares;
        this.perm = perm;
    }

    @PreAuthorize("@perm.canViewExpense(#expenseId)")
    @GetMapping
    public List<ExpenseItemShareDto> listByExpense(@PathVariable Long expenseId) {
        return shares.listByExpense(expenseId).stream()
                .map(ExpenseItemShareDto::fromEntity)
                .toList();
    }

    @PreAuthorize("@perm.canViewExpense(#expenseId)")
    @GetMapping("/mine")
    public List<ExpenseItemShareDto> listMineInExpense(@PathVariable Long expenseId) {
        Long me = perm.currentUserId();
        if (me == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");

        return shares.listByExpenseAndParticipant(expenseId, me).stream()
                .map(ExpenseItemShareDto::fromEntity)
                .toList();
    }
}
