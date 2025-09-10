package com.smartsplit.smartsplitback.model.dto;

import java.math.BigDecimal;

public record ExpenseSettlementDto(
        Long expenseId,
        Long userId,
        BigDecimal owedAmount,
        BigDecimal paidAmount,
        boolean settled,
        BigDecimal remaining
) {}
