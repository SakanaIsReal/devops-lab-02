package com.smartsplit.smartsplitback.model.dto;

import com.smartsplit.smartsplitback.model.PaymentReceipt;

public record PaymentReceiptDto(
        Long id,
        Long paymentId,
        String fileUrl
) {
    public static PaymentReceiptDto fromEntity(PaymentReceipt r) {
        return new PaymentReceiptDto(
                r.getId(),
                (r.getPayment() != null ? r.getPayment().getId() : null),
                r.getFileUrl()
        );
    }
}
