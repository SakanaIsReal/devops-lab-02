package com.smartsplit.smartsplitback.service;

import com.smartsplit.smartsplitback.model.ExpenseItemShare;
import com.smartsplit.smartsplitback.repository.ExpenseItemShareRepository;
import com.smartsplit.smartsplitback.repository.ExpensePaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExpenseSettlementService {

    private final ExpenseItemShareRepository shares;
    private final ExpensePaymentRepository payments;

    public ExpenseSettlementService(ExpenseItemShareRepository shares,
            ExpensePaymentRepository payments) {
        this.shares = shares;
        this.payments = payments;
    }

    @Transactional(readOnly = true)
    public BigDecimal owedForUser(Long expenseId, Long userId) {
        var list = shares.fetchForExpenseAndUser(expenseId, userId);
        BigDecimal sum = BigDecimal.ZERO;

        for (ExpenseItemShare s : list) {
            BigDecimal v = s.getShareValue();
            if (v != null) {
                sum = sum.add(v);
            }
            
        }
        return sum;
    }


    @Transactional(readOnly = true)
    public BigDecimal paidForUser(Long expenseId, Long userId) {
        return payments.sumVerifiedAmountByExpenseIdAndUser(expenseId, userId);
    }

    @Transactional(readOnly = true)
    public com.smartsplit.smartsplitback.model.dto.ExpenseSettlementDto userSettlement(Long expenseId, Long userId) {
        var owed = owedForUser(expenseId, userId);
        var paid = paidForUser(expenseId, userId);
        boolean settled = paid.compareTo(owed) >= 0;
        BigDecimal remaining = owed.subtract(paid);
        if (remaining.signum() < 0)
            remaining = BigDecimal.ZERO;
        return new com.smartsplit.smartsplitback.model.dto.ExpenseSettlementDto(expenseId, userId, owed, paid, settled,
                remaining);
    }

    @Transactional(readOnly = true)
    public List<com.smartsplit.smartsplitback.model.dto.ExpenseSettlementDto> allSettlements(Long expenseId) {
        // รวมรายชื่อ: คนที่มี share + คนที่มี payment VERIFIED
        Set<Long> participants = new HashSet<>(shares.findParticipantIdsByExpense(expenseId));
        participants.addAll(payments.findVerifiedPayerIdsByExpense(expenseId));

        return participants.stream()
                .sorted()
                .map(uid -> userSettlement(expenseId, uid))
                .collect(Collectors.toList());
    }
}
