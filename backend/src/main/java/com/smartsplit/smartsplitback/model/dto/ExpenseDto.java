package com.smartsplit.smartsplitback.model.dto;

import com.smartsplit.smartsplitback.model.ExpenseStatus;
import com.smartsplit.smartsplitback.model.ExpenseType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ExpenseDto(
        Long id,
        Long groupId,
        Long payerUserId,
        BigDecimal amount,
        ExpenseType type,
        String title,
        ExpenseStatus status,
        LocalDateTime createdAt
) {}
