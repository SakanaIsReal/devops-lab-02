// src/main/java/com/smartsplit/smartsplitback/service/ExpensePaymentService.java
package com.smartsplit.smartsplitback.service;

import com.smartsplit.smartsplitback.model.Expense;
import com.smartsplit.smartsplitback.model.ExpensePayment;
import com.smartsplit.smartsplitback.model.PaymentReceipt;
import com.smartsplit.smartsplitback.model.PaymentStatus;
import com.smartsplit.smartsplitback.model.User;
import com.smartsplit.smartsplitback.repository.ExpensePaymentRepository;
import com.smartsplit.smartsplitback.repository.ExpenseRepository;
import com.smartsplit.smartsplitback.repository.PaymentReceiptRepository;
import com.smartsplit.smartsplitback.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class ExpensePaymentService {

    private final ExpensePaymentRepository payments;
    private final ExpenseRepository expenses;
    private final UserRepository users;
    private final PaymentReceiptRepository receipts;

    public ExpensePaymentService(ExpensePaymentRepository payments,
                                 ExpenseRepository expenses,
                                 UserRepository users,
                                 PaymentReceiptRepository receipts) {
        this.payments = payments;
        this.expenses = expenses;
        this.users = users;
        this.receipts = receipts;
    }

    /* ============ READ ============ */

    @Transactional(readOnly = true)
    public List<ExpensePayment> listByExpense(Long expenseId) {
        return payments.findByExpense_Id(expenseId);
    }

    @Transactional(readOnly = true)
    public ExpensePayment get(Long paymentId) {
        return payments.findById(paymentId).orElse(null);
    }

    /** ดึง payment โดยบังคับว่าอยู่ใต้ expenseId */
    @Transactional(readOnly = true)
    public ExpensePayment getInExpense(Long expenseId, Long paymentId) {
        return payments.findByIdAndExpense_Id(paymentId, expenseId).orElse(null);
    }

    /* ============ CREATE / UPDATE / DELETE ============ */

    @Transactional
    public ExpensePayment create(Long expenseId, Long fromUserId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be > 0");
        }

        Expense exp = expenses.findById(expenseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found"));
        User fromUser = users.findById(fromUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User (payer) not found"));

        ExpensePayment p = new ExpensePayment();
        p.setExpense(exp);
        p.setFromUser(fromUser);                            // << ใช้ความสัมพันธ์กับ User (ไม่ใช่แค่ id)
        p.setAmount(scaleMoney(amount));
        p.setStatus(PaymentStatus.PENDING);
        return payments.save(p);
    }

    @Transactional
    public ExpensePayment setStatus(Long paymentId, PaymentStatus status) {
        ExpensePayment p = payments.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
        p.setStatus(status);
        return payments.save(p);
    }

    /** เปลี่ยนสถานะ โดยบังคับว่า payment ∈ expense */
    @Transactional
    public ExpensePayment setStatusInExpense(Long expenseId, Long paymentId, PaymentStatus status) {
        ExpensePayment p = payments.findByIdAndExpense_Id(paymentId, expenseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found in this expense"));
        p.setStatus(status);
        return payments.save(p);
    }

    @Transactional
    public void delete(Long paymentId) {
        if (!payments.existsById(paymentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found");
        }
        payments.deleteById(paymentId);
    }

    /** ลบ โดยบังคับว่า payment ∈ expense */
    @Transactional
    public void deleteInExpense(Long expenseId, Long paymentId) {
        if (!payments.existsByIdAndExpense_Id(paymentId, expenseId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found in this expense");
        }
        payments.deleteById(paymentId);
    }

    /* ============ RECEIPT ============ */

    /** แนบ/อัปเดตสลิปให้กับ payment */
    @Transactional
    public PaymentReceipt attachReceipt(Long paymentId, String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fileUrl is required");
        }

        ExpensePayment p = payments.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));

        // ถ้ามีใบเสร็จอยู่แล้ว → อัปเดต, ถ้าไม่มีก็สร้างใหม่
        PaymentReceipt r = receipts.findByPayment_Id(paymentId).orElseGet(PaymentReceipt::new);
        r.setExpensePayment(p);
        r.setFileUrl(fileUrl.trim());
        return receipts.save(r);
    }

    /** แนบสลิป โดยบังคับว่า payment ∈ expense */
    @Transactional
    public PaymentReceipt attachReceiptInExpense(Long expenseId, Long paymentId, String fileUrl) {
        if (!payments.existsByIdAndExpense_Id(paymentId, expenseId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found in this expense");
        }
        return attachReceipt(paymentId, fileUrl);
    }

    /* ============ AGG ============ */

    @Transactional(readOnly = true)
    public BigDecimal sumVerified(Long expenseId) {
        return payments.sumVerifiedAmountByExpenseId(expenseId);
    }

    /* ============ UTIL ============ */

    private BigDecimal scaleMoney(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
