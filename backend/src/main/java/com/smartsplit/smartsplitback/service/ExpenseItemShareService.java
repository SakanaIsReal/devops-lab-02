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

@Service
public class ExpenseItemShareService {

    private final ExpenseItemShareRepository shares;
    private final ExpenseItemRepository items;
    private final UserRepository users;
    private final GroupMemberRepository members;
    private final ExpenseRepository expenses;

    public ExpenseItemShareService(ExpenseItemShareRepository shares,
                                   ExpenseItemRepository items,
                                   UserRepository users,
                                   GroupMemberRepository members,
                                   ExpenseRepository expenses) {
        this.shares = shares;
        this.items = items;
        this.users = users;
        this.members = members;
        this.expenses = expenses;
    }

    /* ─────────────── READ (scoped) ─────────────── */

    @Transactional(readOnly = true)
    public List<ExpenseItemShare> listByItemInExpense(Long expenseId, Long itemId) {
        assertItemInExpenseOr404(expenseId, itemId);
        return shares.findByExpenseItem_Id(itemId);
    }

    @Transactional(readOnly = true)
    public List<ExpenseItemShare> listByExpense(Long expenseId) {
        return shares.findByExpenseItem_Expense_Id(expenseId);
    }

    /* ─────────────── CREATE (scoped) ─────────────── */

    /** เพิ่ม share: ถ้าส่ง percent มา → คำนวณค่าเป็น share_value (ปัด 2 ตำแหน่ง HALF_UP) */
    @Transactional
    public ExpenseItemShare addShareInExpense(Long expenseId,
                                              Long itemId,
                                              Long participantUserId,
                                              BigDecimal shareValue,
                                              BigDecimal sharePercent) {
        // ยืนยัน item อยู่ใต้ expense
        ExpenseItem item = items.findByIdAndExpense_Id(itemId, expenseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found in this expense"));

        // ยืนยันผู้ใช้
        User user = users.findById(participantUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Participant user not found"));

        // บังคับว่า participant เป็นสมาชิกกลุ่มของ expense นี้
        Long groupId = expenses.findGroupIdByExpenseId(expenseId);
        if (groupId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid expense");
        }
        if (!members.existsByGroup_IdAndUser_Id(groupId, participantUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Participant is not a member of this group");
        }

        // คำนวณ value จาก percent ถ้ามี (priority)
        BigDecimal finalValue = (sharePercent != null)
                ? percentToValue(item.getAmount(), sharePercent)
                : scaleMoney(shareValue);

        ExpenseItemShare s = new ExpenseItemShare();
        s.setExpenseItem(item);
        s.setParticipant(user);
        s.setShareValue(finalValue);
        s.setSharePercent(sharePercent); // เก็บไว้เป็นข้อมูลอ้างอิงได้

        return shares.save(s);
    }

    /* ─────────────── UPDATE (scoped) ─────────────── */

    /** อัปเดต share: ถ้าส่ง percent มา → คำนวณทับ share_value (ปัด 2 ตำแหน่ง HALF_UP) */
    @Transactional
    public ExpenseItemShare updateShareInExpense(Long expenseId,
                                                 Long itemId,
                                                 Long shareId,
                                                 BigDecimal shareValue,
                                                 BigDecimal sharePercent) {
        ExpenseItemShare s = shares
                .findByIdAndExpenseItem_IdAndExpenseItem_Expense_Id(shareId, itemId, expenseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Share not found in this expense/item"));

        // ถ้าส่ง percent มา → คำนวณจาก item.amount แล้วลง share_value
        if (sharePercent != null) {
            BigDecimal computed = percentToValue(s.getExpenseItem() != null ? s.getExpenseItem().getAmount() : null, sharePercent);
            s.setShareValue(computed);
            s.setSharePercent(sharePercent);
        } else if (shareValue != null) {
            // ถ้าไม่ส่ง percent แต่ส่ง value → scale ให้เรียบร้อย
            s.setShareValue(scaleMoney(shareValue));
        }
        // ถ้าไม่ส่งอะไรมาเลย → ไม่เปลี่ยนค่า

        return shares.save(s);
    }

    /* ─────────────── DELETE (scoped) ─────────────── */

    @Transactional
    public void deleteShareInExpense(Long expenseId, Long itemId, Long shareId) {
        boolean ok = shares.existsByIdAndExpenseItem_IdAndExpenseItem_Expense_Id(shareId, itemId, expenseId);
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Share not found in this expense/item");
        }
        shares.deleteById(shareId);
    }

    /* ─────────────── UTIL ─────────────── */

    private void assertItemInExpenseOr404(Long expenseId, Long itemId) {
        boolean exists = items.existsByIdAndExpense_Id(itemId, expenseId);
        if (!exists) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found in this expense");
        }
    }

    /** ปัดเป็นจำนวนเงิน 2 ตำแหน่ง (HALF_UP) */
    private BigDecimal scaleMoney(BigDecimal v) {
        if (v == null) return null;
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    /** แปลงเปอร์เซ็นต์ → มูลค่า จากยอด item.amount (ปัด 2 ตำแหน่ง HALF_UP) */
    private BigDecimal percentToValue(BigDecimal itemAmount, BigDecimal percent) {
        BigDecimal base = (itemAmount != null) ? itemAmount : BigDecimal.ZERO;
        BigDecimal pct  = (percent != null) ? percent : BigDecimal.ZERO;
        // base * pct / 100  (scale 2, HALF_UP)
        return base.multiply(pct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    /* ─────────────── Legacy (ไม่ผูก scope) ───────────────
       คงพฤติกรรม “percent มีสิทธิ์เหนือกว่า” เช่นเดียวกัน
    ---------------------------------------------------------------- */

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

        BigDecimal finalValue = (sharePercent != null)
                ? percentToValue(item.getAmount(), sharePercent)
                : scaleMoney(shareValue);

        ExpenseItemShare s = new ExpenseItemShare();
        s.setExpenseItem(item);
        s.setParticipant(user);
        s.setShareValue(finalValue);
        s.setSharePercent(sharePercent);
        return shares.save(s);
    }

    @Deprecated
    @Transactional
    public ExpenseItemShare updateShare(Long shareId, BigDecimal shareValue, BigDecimal sharePercent) {
        ExpenseItemShare s = shares.findById(shareId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Share not found"));

        if (sharePercent != null) {
            BigDecimal computed = percentToValue(s.getExpenseItem() != null ? s.getExpenseItem().getAmount() : null, sharePercent);
            s.setShareValue(computed);
            s.setSharePercent(sharePercent);
        } else if (shareValue != null) {
            s.setShareValue(scaleMoney(shareValue));
        }
        return shares.save(s);
    }

}
