package com.smartsplit.smartsplitback.service;

import com.smartsplit.smartsplitback.model.ExpenseItemShare;
import com.smartsplit.smartsplitback.model.dto.ExpenseSettlementDto;
import com.smartsplit.smartsplitback.repository.ExpenseItemShareRepository;
import com.smartsplit.smartsplitback.repository.ExpensePaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExpenseSettlementService {

    private static final int DISPLAY_SCALE = 2;

    private final ExpenseItemShareRepository shares;
    private final ExpensePaymentRepository payments;

    public ExpenseSettlementService(ExpenseItemShareRepository shares,
                                    ExpensePaymentRepository payments) {
        this.shares = shares;
        this.payments = payments;
    }

    private static BigDecimal r2(BigDecimal x) {
        return x == null ? BigDecimal.ZERO : x.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public BigDecimal owedForUser(Long expenseId, Long userId) {
        var list = shares.fetchForExpenseAndUser(expenseId, userId);
        BigDecimal sum = BigDecimal.ZERO;
        for (ExpenseItemShare s : list) {
            BigDecimal v = s.getShareValue();
            if (v != null) sum = sum.add(v);
        }

        return sum;
    }

    @Transactional(readOnly = true)
    public BigDecimal paidForUser(Long expenseId, Long userId) {
        BigDecimal v = payments.sumVerifiedAmountByExpenseIdAndUser(expenseId, userId);
        return v == null ? BigDecimal.ZERO : v;
    }

    @Transactional(readOnly = true)
    public ExpenseSettlementDto userSettlement(Long expenseId, Long userId) {
        BigDecimal owed = owedForUser(expenseId, userId);
        BigDecimal paid = paidForUser(expenseId, userId);


        BigDecimal remaining = owed.subtract(paid);
        if (remaining.signum() < 0) remaining = BigDecimal.ZERO;

        // ปัดเป็น 2 ตำแหน่งตอน "ส่งออก"
        BigDecimal owedOut = r2(owed);
        BigDecimal paidOut = r2(paid);
        BigDecimal remOut  = r2(remaining);
        boolean settled = paidOut.compareTo(owedOut) >= 0;

        return new ExpenseSettlementDto(expenseId, userId, owedOut, paidOut, settled, remOut);
    }

    @Transactional(readOnly = true)
    public List<ExpenseSettlementDto> allSettlements(Long expenseId) {
        // รวมรายชื่อ: คนที่มี share + คนที่มี payment VERIFIED
        Set<Long> participants = new HashSet<>(shares.findParticipantIdsByExpense(expenseId));
        participants.addAll(payments.findVerifiedPayerIdsByExpense(expenseId));

        return participants.stream()
                .sorted()
                .map(uid -> userSettlement(expenseId, uid)) // ได้ค่าออกมาเป็น 2 ตำแหน่งแล้ว
                .collect(Collectors.toList());
    }
}
