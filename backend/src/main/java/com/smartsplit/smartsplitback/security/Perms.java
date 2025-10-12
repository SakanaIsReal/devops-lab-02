package com.smartsplit.smartsplitback.security;

import com.smartsplit.smartsplitback.repository.ExpenseItemRepository;
import com.smartsplit.smartsplitback.repository.ExpenseItemShareRepository;
import com.smartsplit.smartsplitback.repository.ExpensePaymentRepository;
import com.smartsplit.smartsplitback.repository.ExpenseRepository;
import com.smartsplit.smartsplitback.repository.GroupMemberRepository;
import com.smartsplit.smartsplitback.repository.GroupRepository;
import org.springframework.stereotype.Component;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import java.lang.reflect.Method;
import java.util.Objects;

@Component("perm")
public class Perms {

    private final SecurityFacade sec;
    private final GroupRepository groups;
    private final GroupMemberRepository members;
    private final ExpenseRepository expenses;
    private final ExpenseItemShareRepository shares;
    private final ExpenseItemRepository expenseItems;
    private final ExpensePaymentRepository payments;

    public Perms(SecurityFacade sec,
                 GroupRepository groups,
                 GroupMemberRepository members,
                 ExpenseRepository expenses,
                 ExpenseItemShareRepository shares,
                 ExpensePaymentRepository payments,
                 ExpenseItemRepository expenseItems) {

        this.sec = sec;
        this.groups = groups;
        this.members = members;
        this.expenses = expenses;
        this.shares = shares;
        this.payments = payments;
        this.expenseItems = expenseItems;
    }
    public Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return extractUserId(auth);
    }

    private Long extractUserId(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No authentication");
        }
        Object p = auth.getPrincipal();

        for (String m : new String[]{"getId", "getUid", "getUserId"}) {
            try {
                Method md = p.getClass().getMethod(m);
                Object v = md.invoke(p);
                if (v != null) return Long.valueOf(String.valueOf(v));
            } catch (Exception ignored) {}
        }

        try {
            return Long.valueOf(auth.getName());
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Cannot resolve user id from token (principal=" + p.getClass().getSimpleName() + ")"
            );
        }
    }
    public boolean canViewUser(Long targetUserId) {
        Long me = sec.currentUserId();
        if (me == null) return false;
        return isAdmin() || isSelf(targetUserId) || members.existsSharedGroup(me, targetUserId);
    }
    public boolean isAdmin() {
        return sec.isAdmin();
    }

    public boolean isSelf(Long userId) {
        Long me = sec.currentUserId();
        return me != null && me.equals(userId);
    }

    public boolean isGroupMember(Long groupId) {
        if (isAdmin()) {
            return true;
        }
        Long me = sec.currentUserId();
        if (me == null) {
            return false;
        }

        Long ownerId = groups.findOwnerIdById(groupId);
        boolean isOwner = ownerId != null && ownerId.equals(me);
        if (isOwner) {
            return true;
        }
        try {
            return members.existsByGroup_IdAndUser_Id(groupId, me);
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean isGroupOwner(Long groupId) {
        if (isAdmin()) return true;
        Long me = sec.currentUserId();
        if (me == null) return false;
        Long ownerId = groups.findOwnerIdById(groupId);
        return ownerId != null && ownerId.equals(me);
    }

    public boolean canManageMembers(Long groupId) {
        return isAdmin() || isGroupOwner(groupId);
    }

    public boolean canManageGroup(Long groupId) {
        return isAdmin() || isGroupOwner(groupId);
    }

    public boolean canCreateExpenseInGroup(Long groupId) {
        return isAdmin() || isGroupMember(groupId);
    }

    public boolean canViewExpense(Long expenseId) {
        if (isAdmin()) return true;
        Long gid = expenses.findGroupIdByExpenseId(expenseId);
        return gid != null && isGroupMember(gid);
    }

    public boolean canManageExpense(Long expenseId) {
        if (isAdmin()) return true;
        Long me = sec.currentUserId();
        if (me == null) return false;

        Long groupId = expenses.findGroupIdByExpenseId(expenseId);
        if (groupId == null) return false;

        Long ownerId = groups.findOwnerIdById(groupId);
        if (ownerId != null && ownerId.equals(me)) return true;

        Long payerId = expenses.findPayerUserIdByExpenseId(expenseId);
        return payerId != null && payerId.equals(me);
    }

    public boolean canSubmitPayment(Long expenseId, Long fromUserId) {
        if (isAdmin()) return true;
        Long me = sec.currentUserId();
        if (me == null) return false;


        if (!Objects.equals(me, fromUserId)) return false;

        // ต้องเป็นสมาชิกของกลุ่มนี้
        Long groupId = expenses.findGroupIdByExpenseId(expenseId);
        if (groupId == null) return false;
        if (!isGroupMember(groupId)) return false;

        // ต้องเป็นผู้มี share ใน expense นี้
        return shares.existsByExpenseItem_Expense_IdAndParticipant_Id(expenseId, me);
    }

    public boolean paymentBelongsToExpense(Long expenseId, Long paymentId) {
        if (expenseId == null || paymentId == null) return false;
        return payments.existsByIdAndExpense_Id(paymentId, expenseId);
    }


    public boolean canViewExpensePayment(Long expenseId, Long paymentId) {
        if (!paymentBelongsToExpense(expenseId, paymentId)) return false;
        if (isAdmin()) return true;
        Long groupId = expenses.findGroupIdByExpenseId(expenseId);
        return groupId != null && isGroupMember(groupId);
    }


    public boolean canManageExpensePayment(Long expenseId, Long paymentId) {
        if (!paymentBelongsToExpense(expenseId, paymentId)) return false;
        return canManageExpense(expenseId);
    }

    public boolean itemBelongsToExpense(Long expenseId, Long itemId) {
        if (expenseId == null || itemId == null) return false;
        return expenseItems.existsByIdAndExpense_Id(itemId, expenseId);
    }

    public boolean canViewExpenseItem(Long expenseId, Long itemId) {
        if (!itemBelongsToExpense(expenseId, itemId)) return false;
        if (isAdmin()) return true;
        Long groupId = expenses.findGroupIdByExpenseId(expenseId);
        if (groupId == null) return false;
        return isGroupMember(groupId);
    }

    public boolean canManageExpenseItem(Long expenseId, Long itemId) {
        if (!itemBelongsToExpense(expenseId, itemId)) return false;
        return canManageExpense(expenseId);
    }



    public boolean shareBelongsToItemInExpense(Long expenseId, Long itemId, Long shareId) {
        if (expenseId == null || itemId == null || shareId == null) return false;
        return shares.existsByIdAndExpenseItem_IdAndExpenseItem_Expense_Id(shareId, itemId, expenseId);
    }


    public boolean canManageExpenseShare(Long expenseId, Long itemId, Long shareId) {
        if (!shareBelongsToItemInExpense(expenseId, itemId, shareId)) return false;
        return canManageExpense(expenseId);
    }
}
