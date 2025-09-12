package com.smartsplit.smartsplitback.controller;

import com.smartsplit.smartsplitback.model.ExpensePayment;
import com.smartsplit.smartsplitback.model.PaymentStatus;
import com.smartsplit.smartsplitback.model.dto.ExpensePaymentDto;
import com.smartsplit.smartsplitback.service.ExpensePaymentService;
import com.smartsplit.smartsplitback.service.FileStorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/expenses/{expenseId}/payments")
public class ExpensePaymentController {

    private final ExpensePaymentService payments;
    private final FileStorageService storage;

    public ExpensePaymentController(ExpensePaymentService payments, FileStorageService storage) {
        this.payments = payments;
        this.storage = storage;
    }

    /** ลิสต์ payments ของ expense (สมาชิกกลุ่ม/แอดมิน) */
    @PreAuthorize("@perm.canViewExpense(#expenseId)")
    @GetMapping
    public List<ExpensePaymentDto> list(@PathVariable Long expenseId) {
        return payments.listByExpense(expenseId).stream()
                .map(ExpensePaymentDto::fromEntity)
                .toList();
    }


    @PreAuthorize("@perm.canViewExpense(#expenseId)")
    @GetMapping("/{paymentId}")
    public ExpensePaymentDto get(@PathVariable Long expenseId, @PathVariable Long paymentId) {
        ExpensePayment p = payments.getInExpense(expenseId, paymentId);
        if (p == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found in this expense");
        return ExpensePaymentDto.fromEntity(p);
    }


    @PreAuthorize("@perm.canSubmitPayment(#expenseId, #fromUserId)")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ExpensePaymentDto create(@PathVariable Long expenseId,
                                    @RequestParam Long fromUserId,
                                    @RequestParam BigDecimal amount,
                                    @RequestPart(name = "receipt", required = false) MultipartFile receipt,
                                    HttpServletRequest req) {


        ExpensePayment p = payments.create(expenseId, fromUserId, amount);


        if (receipt != null && !receipt.isEmpty()) {
            String url = storage.save(receipt, "payment-receipts", "payment-" + p.getId(), req);
            payments.attachReceiptInExpense(expenseId, p.getId(), url); // จะ throw 409 ถ้ามีแล้ว
        }


        return payments.getInExpense(expenseId, p.getId()) != null
                ? ExpensePaymentDto.fromEntity(payments.getInExpense(expenseId, p.getId()))
                : ExpensePaymentDto.fromEntity(p);
    }


    @PreAuthorize("@perm.canManageExpense(#expenseId)")
    @PutMapping("/{paymentId}/status")
    public ExpensePaymentDto setStatus(@PathVariable Long expenseId,
                                       @PathVariable Long paymentId,
                                       @RequestParam PaymentStatus status) {
        return ExpensePaymentDto.fromEntity(payments.setStatusInExpense(expenseId, paymentId, status));
    }


    @PreAuthorize("@perm.canManageExpense(#expenseId)")
    @DeleteMapping("/{paymentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long expenseId, @PathVariable Long paymentId) {
        payments.deleteInExpense(expenseId, paymentId); // service จะจัดการลบไฟล์สลิปให้
    }


//    @PreAuthorize("@perm.canViewExpense(#expenseId)")
//    @GetMapping("/total-verified")
//    public BigDecimal totalVerified(@PathVariable Long expenseId) {
//        return payments.sumVerified(expenseId);
//    }
}
