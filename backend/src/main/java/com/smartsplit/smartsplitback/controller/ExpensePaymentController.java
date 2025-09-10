package com.smartsplit.smartsplitback.controller;

import com.smartsplit.smartsplitback.model.ExpensePayment;
import com.smartsplit.smartsplitback.model.PaymentReceipt;
import com.smartsplit.smartsplitback.model.PaymentStatus;
import com.smartsplit.smartsplitback.model.dto.ExpensePaymentDto;
import com.smartsplit.smartsplitback.model.dto.PaymentReceiptDto;
import com.smartsplit.smartsplitback.service.ExpensePaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/expenses/{expenseId}/payments")
public class ExpensePaymentController {

    private final ExpensePaymentService payments;

    public ExpensePaymentController(ExpensePaymentService payments) {
        this.payments = payments;
    }

    /** ลิสต์ payments ของ expense (สมาชิกกลุ่ม/แอดมิน) */
    @PreAuthorize("@perm.canManageExpense(#expenseId)")
    @GetMapping
    public List<ExpensePaymentDto> list(@PathVariable Long expenseId) {
        return payments.listByExpense(expenseId).stream()
                .map(ExpensePaymentDto::fromEntity)
                .toList();
    }

    /** ดึง payment รายตัว (สมาชิกกลุ่ม/แอดมิน) */
    @PreAuthorize("@perm.canViewExpense(#expenseId)")
    @GetMapping("/{paymentId}")
    public ExpensePaymentDto get(@PathVariable Long expenseId, @PathVariable Long paymentId) {
        ExpensePayment p = payments.get(paymentId);
        if (p == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found");
        return ExpensePaymentDto.fromEntity(p);
    }

    /** สร้าง payment (owner/payer/admin) */
    // imports เพิ่ม (ไม่มีของพิเศษ)
    @PreAuthorize("@perm.canSubmitPayment(#expenseId, #fromUserId)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExpensePaymentDto create(@PathVariable Long expenseId,
                                    @RequestParam Long fromUserId,
                                    @RequestParam BigDecimal amount,
                                    @RequestParam(required = false) String fileUrl) {

        ExpensePayment p = payments.create(expenseId, fromUserId, amount);


        if (fileUrl != null && !fileUrl.isBlank()) {
            payments.attachReceipt(p.getId(), fileUrl);
        }


        return payments.get(p.getId()) != null
                ? ExpensePaymentDto.fromEntity(payments.get(p.getId()))
                : ExpensePaymentDto.fromEntity(p);
    }

    /** เปลี่ยนสถานะ (owner/payer/admin) */
    @PreAuthorize("@perm.canManageExpense(#expenseId)")
    @PutMapping("/{paymentId}/status")
    public ExpensePaymentDto setStatus(@PathVariable Long expenseId,
                                       @PathVariable Long paymentId,
                                       @RequestParam PaymentStatus status) {
        return ExpensePaymentDto.fromEntity(payments.setStatus(paymentId, status));
    }



    /** ลบ payment (owner/payer/admin) */
    @PreAuthorize("@perm.canManageExpense(#expenseId)")
    @DeleteMapping("/{paymentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long expenseId, @PathVariable Long paymentId) {
        payments.delete(paymentId);
    }

    /** รวมยอดที่ยืนยันแล้ว (สมาชิกกลุ่ม/แอดมิน) */
    @PreAuthorize("@perm.canViewExpense(#expenseId)")
    @GetMapping("/total-verified")
    public BigDecimal totalVerified(@PathVariable Long expenseId) {
        return payments.sumVerified(expenseId);
    }
}
