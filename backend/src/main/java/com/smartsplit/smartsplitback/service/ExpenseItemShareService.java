package com.smartsplit.smartsplitback.service;

import com.smartsplit.smartsplitback.model.ExpenseItem;
import com.smartsplit.smartsplitback.model.ExpenseItemShare;
import com.smartsplit.smartsplitback.model.User;
import com.smartsplit.smartsplitback.repository.ExpenseItemRepository;
import com.smartsplit.smartsplitback.repository.ExpenseItemShareRepository;
import com.smartsplit.smartsplitback.repository.ExpenseRepository;
import com.smartsplit.smartsplitback.repository.GroupMemberRepository;
import com.smartsplit.smartsplitback.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ExpenseItemShareService {

    private final ExpenseItemShareRepository shares;
    private final ExpenseItemRepository items;
    private final UserRepository users;
    private final GroupMemberRepository members;
    private final ExpenseRepository expenses;
    private final ExchangeRateService fx; // 🔹 เพิ่ม

    public ExpenseItemShareService(ExpenseItemShareRepository shares,
                                   ExpenseItemRepository items,
                                   UserRepository users,
                                   GroupMemberRepository members,
                                   ExpenseRepository expenses,
                                   ExchangeRateService fx) { // 🔹 เพิ่ม
        this.shares = shares;
        this.items = items;
        this.users = users;
        this.members = members;
        this.expenses = expenses;
        this.fx = fx; // 🔹 เพิ่ม
    }

    @Transactional(readOnly = true)
    public List<ExpenseItemShare> listByItemInExpense(Long expenseId, Long itemId) {
        assertItemInExpenseOr404(expenseId, itemId);
        return shares.findByExpenseItem_Id(itemId);
    }

    @Transactional(readOnly = true)
    public List<ExpenseItemShare> listByExpense(Long expenseId) {
        return shares.findByExpenseItem_Expense_Id(expenseId);
    }

    @Transactional
    public ExpenseItemShare addShareInExpense(Long expenseId,
                                              Long itemId,
                                              Long participantUserId,
                                              BigDecimal shareValue,
                                              BigDecimal sharePercent) {
        ExpenseItem item = items.findByIdAndExpense_Id(itemId, expenseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found in this expense"));

        User user = users.findById(participantUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Participant user not found"));

        BigDecimal original;
        if (sharePercent != null) {
            original = percentToValue(item.getAmount(), sharePercent);
        } else {
            if (shareValue == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Either shareValue or sharePercent is required");
            }
            original = scaleMoney(shareValue);
        }

        String ccy = safeUpper(itemCurrency(item));
        Map<String, BigDecimal> rates = fx.getRatesToThb(item.getExpense());
        BigDecimal thb = fx.toThb(ccy, original, rates);

        ExpenseItemShare s = new ExpenseItemShare();
        s.setExpenseItem(item);
        s.setParticipant(user);
        s.setShareOriginalValue(original);
        s.setShareValue(thb);
        s.setSharePercent(sharePercent);

        return shares.save(s);
    }

    @Transactional
    public ExpenseItemShare updateShareInExpense(Long expenseId,
                                                 Long itemId,
                                                 Long shareId,
                                                 BigDecimal shareValue,
                                                 BigDecimal sharePercent) {
        ExpenseItemShare s = shares
                .findByIdAndExpenseItem_IdAndExpenseItem_Expense_Id(shareId, itemId, expenseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Share not found in this expense/item"));

        ExpenseItem item = s.getExpenseItem();
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Share inconsistent: missing item");
        }

        BigDecimal original = s.getShareOriginalValue();
        if (sharePercent != null) {

            original = percentToValue(item.getAmount(), sharePercent);
            s.setSharePercent(sharePercent);
        } else if (shareValue != null) {
            original = scaleMoney(shareValue);
        }

        String ccy = safeUpper(itemCurrency(item));
        Map<String, BigDecimal> rates = fx.getRatesToThb(item.getExpense());
        BigDecimal thb = fx.toThb(ccy, original, rates);

        s.setShareOriginalValue(original);
        s.setShareValue(thb);

        return shares.save(s);
    }

    @Transactional
    public void deleteShareInExpense(Long expenseId, Long itemId, Long shareId) {
        boolean ok = shares.existsByIdAndExpenseItem_IdAndExpenseItem_Expense_Id(shareId, itemId, expenseId);
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Share not found in this expense/item");
        }
        shares.deleteById(shareId);
    }

    private void assertItemInExpenseOr404(Long expenseId, Long itemId) {
        boolean exists = items.existsByIdAndExpense_Id(itemId, expenseId);
        if (!exists) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found in this expense");
        }
    }

    private BigDecimal scaleMoney(BigDecimal v) {
        if (v == null) return null;
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentToValue(BigDecimal itemAmount, BigDecimal percent) {
        BigDecimal base = (itemAmount != null) ? itemAmount : BigDecimal.ZERO;
        BigDecimal pct  = (percent != null) ? percent : BigDecimal.ZERO;
        // base * pct / 100  (scale 2, HALF_UP)
        return base.multiply(pct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    // ---------- helpers ----------
    /** ดึงสกุลเงินจาก item; ถ้าไม่มี ให้ THB */
    private String itemCurrency(ExpenseItem item) {
        try {
            var c = (String) ExpenseItem.class.getMethod("getCurrency").invoke(item);
            return (c == null || c.isBlank()) ? "THB" : c;
        } catch (Exception e) {
            // กรณีโปรเจกต์บางเวอร์ชันยังไม่มี field currency ใน ExpenseItem
            return "THB";
        }
    }

    private String safeUpper(String s) {
        return (s == null) ? null : s.toUpperCase(Locale.ROOT);
    }

    // --------- methods marked @Deprecated kept as-is ---------

    @Deprecated
    @Transactional(readOnly = true)
    public List<ExpenseItemShare> listByItem(Long expenseItemId) {
        return shares.findByExpenseItem_Id(expenseItemId);
    }

    @Deprecated
    @Transactional
    public ExpenseItemShare addShare(Long expenseItemId, Long participantUserId,
                                     BigDecimal shareValue, BigDecimal sharePercent) {
        ExpenseItem item = items.findById(expenseItemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense item not found"));
        User user = users.findById(participantUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Participant user not found"));

        BigDecimal original = (sharePercent != null)
                ? percentToValue(item.getAmount(), sharePercent)
                : scaleMoney(shareValue);

        String ccy = safeUpper(itemCurrency(item));
        Map<String, BigDecimal> rates = fx.getRatesToThb(item.getExpense());
        BigDecimal thb = fx.toThb(ccy, original, rates);

        ExpenseItemShare s = new ExpenseItemShare();
        s.setExpenseItem(item);
        s.setParticipant(user);
        s.setShareOriginalValue(original);
        s.setShareValue(thb);
        s.setSharePercent(sharePercent);
        return shares.save(s);
    }

    @Deprecated
    @Transactional
    public ExpenseItemShare updateShare(Long shareId, BigDecimal shareValue, BigDecimal sharePercent) {
        ExpenseItemShare s = shares.findById(shareId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Share not found"));

        ExpenseItem item = s.getExpenseItem();
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Share inconsistent: missing item");
        }

        BigDecimal original = s.getShareOriginalValue();
        if (sharePercent != null) {
            original = percentToValue(item.getAmount(), sharePercent);
            s.setSharePercent(sharePercent);
        } else if (shareValue != null) {
            original = scaleMoney(shareValue);
        }

        String ccy = safeUpper(itemCurrency(item));
        Map<String, BigDecimal> rates = fx.getRatesToThb(item.getExpense());
        BigDecimal thb = fx.toThb(ccy, original, rates);

        s.setShareOriginalValue(original);
        s.setShareValue(thb);
        return shares.save(s);
    }

    @Transactional(readOnly = true)
    public List<ExpenseItemShare> listByExpenseAndParticipant(Long expenseId, Long userId) {
        if (expenseId == null || userId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "expenseId/userId is required");
        }
        return shares.findByExpenseItem_Expense_IdAndParticipant_Id(expenseId, userId);
    }
}
