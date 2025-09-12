package com.smartsplit.smartsplitback.model.dto;

import java.math.BigDecimal;
import com.smartsplit.smartsplitback.model.ExpenseItemShare;

public record ExpenseItemShareDto(
        Long id,
        Long expenseItemId,
        Long participantUserId,
        BigDecimal shareValue,
        BigDecimal sharePercent
) {
    public static ExpenseItemShareDto fromEntity(ExpenseItemShare s) {
        return new ExpenseItemShareDto(
                s.getId(),
                (s.getExpenseItem() != null ? s.getExpenseItem().getId() : null),
                (s.getParticipant() != null ? s.getParticipant().getId() : null),
                s.getShareValue(),
                s.getSharePercent()
        );
    }
}
