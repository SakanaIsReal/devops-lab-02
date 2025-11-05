package com.smartsplit.smartsplitback.model.dto;

import com.smartsplit.smartsplitback.model.ExpenseItem;

import java.math.BigDecimal;

public record ExpenseItemDto(
        Long id,
        Long expenseId,
        String name,
        BigDecimal amount,
        String currency,
        BigDecimal amountThb
) {
    public static ExpenseItemDto fromEntity(ExpenseItem it, BigDecimal amountThb) {
        return new ExpenseItemDto(
                it.getId(),
                it.getExpense() != null ? it.getExpense().getId() : null,
                it.getName(),
                it.getAmount(),
                it.getCurrency(),
                amountThb
        );
    }

    public static ExpenseItemDto fromEntity(ExpenseItem it) {
        return fromEntity(it, null);
    }
}
