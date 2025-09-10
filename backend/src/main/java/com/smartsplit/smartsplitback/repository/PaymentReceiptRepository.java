package com.smartsplit.smartsplitback.repository;

import com.smartsplit.smartsplitback.model.PaymentReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentReceiptRepository extends JpaRepository<PaymentReceipt, Long> {

    Optional<PaymentReceipt> findByPayment_Id(Long paymentId);


}
