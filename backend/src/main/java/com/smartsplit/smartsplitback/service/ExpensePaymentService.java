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
    private final FileStorageService storage;

    public ExpensePaymentService(ExpensePaymentRepository payments,
                                 ExpenseRepository expenses,
                                 UserRepository users,
                                 PaymentReceiptRepository receipts,
                                 FileStorageService storage) {
        this.payments = payments;
        this.expenses = expenses;
        this.users = users;
        this.receipts = receipts;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public List<ExpensePayment> listByExpense(Long expenseId) {
        return payments.findByExpense_Id(expenseId);
    }

    @Transactional(readOnly = true)
    public ExpensePayment get(Long paymentId) {
        return payments.findById(paymentId).orElse(null);
    }


    @Transactional(readOnly = true)
    public ExpensePayment getInExpense(Long expenseId, Long paymentId) {
        return payments.findByIdAndExpense_Id(paymentId, expenseId).orElse(null);
    }


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
        p.setFromUser(fromUser);
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

    @Transactional
    public ExpensePayment setStatusInExpense(Long expenseId, Long paymentId, PaymentStatus status) {
        ExpensePayment p = payments.findByIdAndExpense_Id(paymentId, expenseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found in this expense"));
        p.setStatus(status);
        return payments.save(p);
    }

    @Transactional
    public void delete(Long paymentId) {

        receipts.findByPayment_Id(paymentId).ifPresent(r -> {
            storage.deleteByUrl(r.getFileUrl());
            receipts.delete(r);
        });

        if (!payments.existsById(paymentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found");
        }
        payments.deleteById(paymentId);
    }

    @Transactional
    public void deleteInExpense(Long expenseId, Long paymentId) {
        ExpensePayment p = payments.findByIdAndExpense_Id(paymentId, expenseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found in this expense"));


        receipts.findByPayment_Id(p.getId()).ifPresent(r -> {
            storage.deleteByUrl(r.getFileUrl());
            receipts.delete(r);
        });

        payments.deleteById(p.getId());
    }


    @Transactional
    public PaymentReceipt attachReceipt(Long paymentId, String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fileUrl is required");
        }

        ExpensePayment p = payments.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));

        if (receipts.findByPayment_Id(paymentId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Receipt already exists for this payment");
        }

        PaymentReceipt r = new PaymentReceipt();
        r.setExpensePayment(p);
        r.setFileUrl(fileUrl.trim());
        return receipts.save(r);
    }

    @Transactional
    public PaymentReceipt attachReceiptInExpense(Long expenseId, Long paymentId, String fileUrl) {
        if (!payments.existsByIdAndExpense_Id(paymentId, expenseId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found in this expense");
        }
        if (receipts.findByPayment_Id(paymentId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Receipt already exists for this payment");
        }
        return attachReceipt(paymentId, fileUrl);
    }



    @Transactional(readOnly = true)
    public BigDecimal sumVerified(Long expenseId) {
        return payments.sumVerifiedAmountByExpenseId(expenseId);
    }


    private BigDecimal scaleMoney(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
