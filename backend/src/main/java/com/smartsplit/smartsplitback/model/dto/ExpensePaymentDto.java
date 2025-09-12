package com.smartsplit.smartsplitback.model.dto;

import java.math.BigDecimal;
import java.time.Instant;
import com.smartsplit.smartsplitback.model.ExpensePayment;
import com.smartsplit.smartsplitback.model.PaymentStatus;

public record ExpensePaymentDto(
        Long id,
        Long expenseId,
        Long fromUserId,
        BigDecimal amount,
        PaymentStatus status,
        Instant createdAt,
        Instant verifiedAt,
        Long receiptId,
        String receiptFileUrl
) {
    public static ExpensePaymentDto fromEntity(ExpensePayment p) {
        return new ExpensePaymentDto(
                p.getId(),
                (p.getExpense() != null ? p.getExpense().getId() : null),
                (p.getFromUser() != null ? p.getFromUser().getId() : null),
                p.getAmount(),
                p.getStatus(),
                p.getCreatedAt(),
                p.getVerifiedAt(),
                (p.getReceipt() != null ? p.getReceipt().getId() : null),
                (p.getReceipt() != null ? p.getReceipt().getFileUrl() : null)
        );
    }
}
