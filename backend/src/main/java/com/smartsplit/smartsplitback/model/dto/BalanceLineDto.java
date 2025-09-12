package com.smartsplit.smartsplitback.model.dto;

import java.math.BigDecimal;

public record BalanceLineDto(
        String direction,                 // "YOU_OWE" | "OWES_YOU"
        Long counterpartyUserId,
        String counterpartyUserName,
        String counterpartyAvatarUrl,
        Long groupId,
        String groupName,
        Long expenseId,
        String expenseTitle,
        BigDecimal remaining
) {}
