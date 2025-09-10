package com.smartsplit.smartsplitback.model.dto;

import java.math.BigDecimal;
import com.smartsplit.smartsplitback.model.ExpenseItem;

public record ExpenseItemDto(
        Long id,
        Long expenseId,
        String name,
        BigDecimal amount
) {
    public static ExpenseItemDto fromEntity(ExpenseItem e) {
        return new ExpenseItemDto(
                e.getId(),
                (e.getExpense() != null ? e.getExpense().getId() : null),
                e.getName(),
                e.getAmount()
        );
    }
}
