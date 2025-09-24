
package com.smartsplit.smartsplitback.controller;

import com.smartsplit.smartsplitback.model.dto.ExpenseItemShareDto;
import com.smartsplit.smartsplitback.security.Perms;
import com.smartsplit.smartsplitback.service.ExpenseItemShareService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/expenses/{expenseId}/items/{itemId}/shares")
public class ExpenseItemShareController {

    private final ExpenseItemShareService shares;
    private final Perms perm;

    public ExpenseItemShareController(ExpenseItemShareService shares, Perms perm) {
        this.shares = shares;
        this.perm = perm;
    }

    @PreAuthorize("@perm.canViewExpenseItem(#expenseId, #itemId)")
    @GetMapping
    public List<ExpenseItemShareDto> listByItem(@PathVariable Long expenseId,
                                                @PathVariable Long itemId) {
        return shares.listByItemInExpense(expenseId, itemId)
                .stream().map(ExpenseItemShareDto::fromEntity).toList();
    }

    @PreAuthorize("@perm.canManageExpenseItem(#expenseId, #itemId)")
    @PostMapping
    public ExpenseItemShareDto add(@PathVariable Long expenseId,
                                   @PathVariable Long itemId,
                                   @RequestParam Long participantUserId,
                                   @RequestParam(required = false) BigDecimal shareValue,
                                   @RequestParam(required = false) BigDecimal sharePercent) {
     
        if (shareValue == null && sharePercent == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Either shareValue or sharePercent is required");
        }
        if (sharePercent != null &&
                (sharePercent.compareTo(BigDecimal.ZERO) < 0 ||
                        sharePercent.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "sharePercent must be between 0 and 100");
        }

        var s = shares.addShareInExpense(expenseId, itemId, participantUserId, shareValue, sharePercent);
        return ExpenseItemShareDto.fromEntity(s);
    }


    @PreAuthorize("@perm.canManageExpenseShare(#expenseId, #itemId, #shareId)")
    @PutMapping("/{shareId}")
    public ExpenseItemShareDto update(@PathVariable Long expenseId,
                                      @PathVariable Long itemId,
                                      @PathVariable Long shareId,
                                      @RequestParam(required = false) BigDecimal shareValue,
                                      @RequestParam(required = false) BigDecimal sharePercent) {
        if (shareValue == null && sharePercent == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Either shareValue or sharePercent is required");
        }
        if (sharePercent != null &&
                (sharePercent.compareTo(BigDecimal.ZERO) < 0 ||
                        sharePercent.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "sharePercent must be between 0 and 100");
        }

        var s = shares.updateShareInExpense(expenseId, itemId, shareId, shareValue, sharePercent);
        return ExpenseItemShareDto.fromEntity(s);
    }

    @PreAuthorize("@perm.canManageExpenseShare(#expenseId, #itemId, #shareId)")
    @DeleteMapping("/{shareId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long expenseId,
                       @PathVariable Long itemId,
                       @PathVariable Long shareId) {
        shares.deleteShareInExpense(expenseId, itemId, shareId);
    }
}
