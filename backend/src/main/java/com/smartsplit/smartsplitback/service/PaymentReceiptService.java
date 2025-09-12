package com.smartsplit.smartsplitback.service;

import com.smartsplit.smartsplitback.model.PaymentReceipt;
import com.smartsplit.smartsplitback.repository.PaymentReceiptRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentReceiptService {

    private final PaymentReceiptRepository receipts;

    public PaymentReceiptService(PaymentReceiptRepository receipts) {
        this.receipts = receipts;
    }

    @Transactional(readOnly = true)
    public PaymentReceipt get(Long id) {
        return receipts.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public PaymentReceipt getByPaymentId(Long paymentId) {
        return receipts.findByPayment_Id(paymentId).orElse(null);
    }

    @Transactional
    public void delete(Long id) {
        if (!receipts.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Receipt not found");
        }
        receipts.deleteById(id);
    }
}
