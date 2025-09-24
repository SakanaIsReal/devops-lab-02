package com.smartsplit.smartsplitback.service;

import com.smartsplit.smartsplitback.model.ExpenseItem;
import com.smartsplit.smartsplitback.model.ExpenseItemShare;
import com.smartsplit.smartsplitback.model.User;
import com.smartsplit.smartsplitback.repository.ExpenseItemRepository;
import com.smartsplit.smartsplitback.repository.ExpenseItemShareRepository;
import com.smartsplit.smartsplitback.repository.ExpenseRepository;
import com.smartsplit.smartsplitback.repository.GroupMemberRepository;
import com.smartsplit.smartsplitback.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExpenseItemShareServiceTest {

    @Mock private ExpenseItemShareRepository shares;
    @Mock private ExpenseItemRepository items;
    @Mock private UserRepository users;
    @Mock private GroupMemberRepository members;
    @Mock private ExpenseRepository expenses;

    @InjectMocks private ExpenseItemShareService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // ===== Helpers =====
    private static ExpenseItem item(Long id, BigDecimal amount) {
        ExpenseItem it = new ExpenseItem();
        it.setId(id);
        it.setAmount(amount);
        return it;
    }

    private static User user(Long id, String email) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        return u;
    }

    private static ExpenseItemShare share(Long id, ExpenseItem it, User u, BigDecimal value, BigDecimal percent) {
        ExpenseItemShare s = new ExpenseItemShare();
        s.setId(id);
        s.setExpenseItem(it);
        s.setParticipant(u);
        s.setShareValue(value);
        s.setSharePercent(percent);
        return s;
    }

    // ================================= listByItemInExpense =================================
    @Nested
    @DisplayName("listByItemInExpense(expenseId, itemId)")
    class ListByItemInExpense {

        @Test
        @DisplayName("item อยู่ใต้ expense → คืนลิสต์ shares ของ item นั้น")
        void ok() {
            Long expenseId = 10L, itemId = 100L;
            when(items.existsByIdAndExpense_Id(itemId, expenseId)).thenReturn(true);

            List<ExpenseItemShare> mock = List.of(
                    share(1L, item(itemId, new BigDecimal("10.00")),
                            user(2L, "a@example.com"), new BigDecimal("3.33"), new BigDecimal("33.33"))
            );
            when(shares.findByExpenseItem_Id(itemId)).thenReturn(mock);

            List<ExpenseItemShare> result = service.listByItemInExpense(expenseId, itemId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(1L);

            verify(items).existsByIdAndExpense_Id(itemId, expenseId);
            verify(shares).findByExpenseItem_Id(itemId);
        }

        @Test
        @DisplayName("item ไม่อยู่ใต้ expense → 404")
        void notFound() {
            Long expenseId = 10L, itemId = 999L;
            when(items.existsByIdAndExpense_Id(itemId, expenseId)).thenReturn(false);

            ResponseStatusException ex = catchThrowableOfType(
                    () -> service.listByItemInExpense(expenseId, itemId),
                    ResponseStatusException.class
            );
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value());
            assertThat(ex.getReason()).isEqualTo("Item not found in this expense");

            verify(items).existsByIdAndExpense_Id(itemId, expenseId);
            verifyNoInteractions(shares);
        }
    }

    // ================================= listByExpense =================================
    @Test
    @DisplayName("listByExpense(expenseId): คืน shares ทั้งหมดใน expense")
    void listByExpense_ok() {
        Long expenseId = 20L;
        List<ExpenseItemShare> mock = List.of(
                share(1L, item(100L, new BigDecimal("10.00")), user(2L, "a@x"), new BigDecimal("5.00"), null),
                share(2L, item(101L, new BigDecimal("9.99")), user(3L, "b@x"), new BigDecimal("3.33"), new BigDecimal("33.33"))
        );
        when(shares.findByExpenseItem_Expense_Id(expenseId)).thenReturn(mock);

        List<ExpenseItemShare> result = service.listByExpense(expenseId);
        assertThat(result).hasSize(2);

        verify(shares).findByExpenseItem_Expense_Id(expenseId);
    }

    // ================================= addShareInExpense =================================
    @Nested
    @DisplayName("addShareInExpense(expenseId, itemId, userId, shareValue, sharePercent)")
    class AddShareInExpense {

        @Test
        @DisplayName("happy path (percent): คำนวณจาก percent → scale 2 ทศนิยม → save")
        void addPercent_ok() {
            Long expenseId = 30L, itemId = 300L, userId = 3L, groupId = 7L;
            ExpenseItem it = item(itemId, new BigDecimal("100.00"));
            User u = user(userId, "u@example.com");

            when(items.findByIdAndExpense_Id(itemId, expenseId)).thenReturn(Optional.of(it));
            when(users.findById(userId)).thenReturn(Optional.of(u));
            when(expenses.findGroupIdByExpenseId(expenseId)).thenReturn(groupId);
            when(members.existsByGroup_IdAndUser_Id(groupId, userId)).thenReturn(true);

            when(shares.save(any(ExpenseItemShare.class))).thenAnswer(inv -> {
                ExpenseItemShare s = inv.getArgument(0);
                s.setId(999L);
                return s;
            });

            ExpenseItemShare created = service.addShareInExpense(
                    expenseId, itemId, userId, null, new BigDecimal("12.345")
            );

            assertThat(created.getId()).isEqualTo(999L);
            assertThat(created.getExpenseItem()).isSameAs(it);
            assertThat(created.getParticipant()).isSameAs(u);
            // 100 * 12.345 / 100 = 12.345 → scale(2, HALF_UP) = 12.35
            assertThat(created.getShareValue()).isEqualByComparingTo("12.35");
            assertThat(created.getSharePercent()).isEqualByComparingTo("12.345");

            verify(items).findByIdAndExpense_Id(itemId, expenseId);
            verify(users).findById(userId);
            verify(expenses).findGroupIdByExpenseId(expenseId);
            verify(members).existsByGroup_IdAndUser_Id(groupId, userId);
            verify(shares).save(any(ExpenseItemShare.class));
        }

        @Test
        @DisplayName("happy path (value): ไม่ส่ง percent → ใช้ shareValue แล้ว scale 2 ทศนิยม")
        void addValue_ok() {
            Long expenseId = 31L, itemId = 301L, userId = 4L, groupId = 8L;
            ExpenseItem it = item(itemId, new BigDecimal("50.00"));
            User u = user(userId, "u2@example.com");

            when(items.findByIdAndExpense_Id(itemId, expenseId)).thenReturn(Optional.of(it));
            when(users.findById(userId)).thenReturn(Optional.of(u));
            when(expenses.findGroupIdByExpenseId(expenseId)).thenReturn(groupId);
            when(members.existsByGroup_IdAndUser_Id(groupId, userId)).thenReturn(true);
            when(shares.save(any(ExpenseItemShare.class))).thenAnswer(inv -> inv.getArgument(0));

            ExpenseItemShare created = service.addShareInExpense(
                    expenseId, itemId, userId, new BigDecimal("1.234"), null
            );

            // scaleMoney(1.234) -> 1.23
            assertThat(created.getShareValue()).isEqualByComparingTo("1.23");
            assertThat(created.getSharePercent()).isNull();
        }

        @Test
        @DisplayName("item ไม่อยู่ใน expense → 404")
        void itemNotInExpense_404() {
            when(items.findByIdAndExpense_Id(999L, 30L)).thenReturn(Optional.empty());

            ResponseStatusException ex = catchThrowableOfType(
                    () -> service.addShareInExpense(30L, 999L, 1L, BigDecimal.ONE, null),
                    ResponseStatusException.class
            );
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value());
            assertThat(ex.getReason()).isEqualTo("Item not found in this expense");

            verify(items).findByIdAndExpense_Id(999L, 30L);
            verifyNoInteractions(users, expenses, members, shares);
        }

        @Test
        @DisplayName("user ไม่พบ → 404")
        void userNotFound_404() {
            Long expenseId = 30L, itemId = 300L, userId = 999L;
            when(items.findByIdAndExpense_Id(itemId, expenseId)).thenReturn(Optional.of(item(itemId, BigDecimal.TEN)));
            when(users.findById(userId)).thenReturn(Optional.empty());

            ResponseStatusException ex = catchThrowableOfType(
                    () -> service.addShareInExpense(expenseId, itemId, userId, BigDecimal.ONE, null),
                    ResponseStatusException.class
            );
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value());
            assertThat(ex.getReason()).isEqualTo("Participant user not found");

            verify(users).findById(userId);
            verifyNoInteractions(expenses, members, shares);
        }

        @Test
        @DisplayName("expense ไม่ถูกต้อง (groupId=null) → 400 BAD_REQUEST")
        void invalidExpense_400() {
            Long expenseId = 30L, itemId = 300L, userId = 3L;
            when(items.findByIdAndExpense_Id(itemId, expenseId)).thenReturn(Optional.of(item(itemId, BigDecimal.ONE)));
            when(users.findById(userId)).thenReturn(Optional.of(user(userId, "u@x")));
            when(expenses.findGroupIdByExpenseId(expenseId)).thenReturn(null);

            ResponseStatusException ex = catchThrowableOfType(
                    () -> service.addShareInExpense(expenseId, itemId, userId, BigDecimal.ONE, null),
                    ResponseStatusException.class
            );
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value());
            assertThat(ex.getReason()).isEqualTo("Invalid expense");

            verify(expenses).findGroupIdByExpenseId(expenseId);
            verifyNoInteractions(members, shares);
        }

        @Test
        @DisplayName("ผู้เข้าร่วมไม่ใช่สมาชิก group → 400 BAD_REQUEST")
        void notGroupMember_400() {
            Long expenseId = 30L, itemId = 300L, userId = 3L, groupId = 7L;
            when(items.findByIdAndExpense_Id(itemId, expenseId)).thenReturn(Optional.of(item(itemId, BigDecimal.ONE)));
            when(users.findById(userId)).thenReturn(Optional.of(user(userId, "u@x")));
            when(expenses.findGroupIdByExpenseId(expenseId)).thenReturn(groupId);
            when(members.existsByGroup_IdAndUser_Id(groupId, userId)).thenReturn(false);

            ResponseStatusException ex = catchThrowableOfType(
                    () -> service.addShareInExpense(expenseId, itemId, userId, BigDecimal.ONE, null),
                    ResponseStatusException.class
            );
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value());
            assertThat(ex.getReason()).isEqualTo("Participant is not a member of this group");

            verify(members).existsByGroup_IdAndUser_Id(groupId, userId);
            verifyNoInteractions(shares);
        }
    }

    // ================================= updateShareInExpense =================================
    @Nested
    @DisplayName("updateShareInExpense(expenseId, itemId, shareId, shareValue, sharePercent)")
    class UpdateShareInExpense {

        @Test
        @DisplayName("ส่ง percent → คำนวณจาก item.amount → อัปเดต value และ percent → save")
        void updateWithPercent_ok() {
            Long expenseId = 40L, itemId = 400L, shareId = 4L;
            ExpenseItem it = item(itemId, new BigDecimal("200.00"));
            ExpenseItemShare existing = share(shareId, it, user(9L, "p@x"), new BigDecimal("0"), null);

            when(shares.findByIdAndExpenseItem_IdAndExpenseItem_Expense_Id(shareId, itemId, expenseId))
                    .thenReturn(Optional.of(existing));
            when(shares.save(any(ExpenseItemShare.class))).thenAnswer(inv -> inv.getArgument(0));

            ExpenseItemShare updated = service.updateShareInExpense(
                    expenseId, itemId, shareId, null, new BigDecimal("33.333")
            );

            // 200 * 33.333% = 66.666 → scale 2 = 66.67
            assertThat(updated.getShareValue()).isEqualByComparingTo("66.67");
            assertThat(updated.getSharePercent()).isEqualByComparingTo("33.333");

            verify(shares).save(existing);
        }

        @Test
        @DisplayName("ส่ง value อย่างเดียว → scale 2 → save")
        void updateWithValue_ok() {
            Long expenseId = 40L, itemId = 400L, shareId = 5L;
            ExpenseItemShare existing = share(shareId, item(itemId, new BigDecimal("50.00")), user(9L, "p@x"), null, null);

            when(shares.findByIdAndExpenseItem_IdAndExpenseItem_Expense_Id(shareId, itemId, expenseId))
                    .thenReturn(Optional.of(existing));
            when(shares.save(any(ExpenseItemShare.class))).thenAnswer(inv -> inv.getArgument(0));

            ExpenseItemShare updated = service.updateShareInExpense(
                    expenseId, itemId, shareId, new BigDecimal("1.999"), null
            );

            assertThat(updated.getShareValue()).isEqualByComparingTo("2.00");
            assertThat(updated.getSharePercent()).isNull();
            verify(shares).save(existing);
        }

        @Test
        @DisplayName("ไม่ส่งอะไรเลย → ไม่เปลี่ยนค่า → save (ยังคงค่าเดิม)")
        void updateNoFields_ok() {
            Long expenseId = 40L, itemId = 400L, shareId = 6L;
            ExpenseItemShare existing = share(shareId, item(itemId, new BigDecimal("10.00")),
                    user(9L, "p@x"), new BigDecimal("3.00"), new BigDecimal("30"));

            when(shares.findByIdAndExpenseItem_IdAndExpenseItem_Expense_Id(shareId, itemId, expenseId))
                    .thenReturn(Optional.of(existing));
            when(shares.save(any(ExpenseItemShare.class))).thenAnswer(inv -> inv.getArgument(0));

            ExpenseItemShare updated = service.updateShareInExpense(expenseId, itemId, shareId, null, null);

            assertThat(updated.getShareValue()).isEqualByComparingTo("3.00");
            assertThat(updated.getSharePercent()).isEqualByComparingTo("30");

            verify(shares).save(existing);
        }

        @Test
        @DisplayName("หา share ไม่พบใน expense/item ที่ระบุ → 404")
        void updateNotFound() {
            when(shares.findByIdAndExpenseItem_IdAndExpenseItem_Expense_Id(9L, 99L, 999L))
                    .thenReturn(Optional.empty());

            ResponseStatusException ex = catchThrowableOfType(
                    () -> service.updateShareInExpense(999L, 99L, 9L, BigDecimal.ONE, null),
                    ResponseStatusException.class
            );
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value());
            assertThat(ex.getReason()).isEqualTo("Share not found in this expense/item");

            verify(shares).findByIdAndExpenseItem_IdAndExpenseItem_Expense_Id(9L, 99L, 999L);
            verify(shares, never()).save(any());
        }
    }

    // ================================= deleteShareInExpense =================================
    @Nested
    @DisplayName("deleteShareInExpense(expenseId, itemId, shareId)")
    class DeleteShareInExpense {

        @Test
        @DisplayName("มีอยู่จริง → ลบได้")
        void deleteOk() {
            when(shares.existsByIdAndExpenseItem_IdAndExpenseItem_Expense_Id(5L, 50L, 500L))
                    .thenReturn(true);

            service.deleteShareInExpense(500L, 50L, 5L);

            verify(shares).existsByIdAndExpenseItem_IdAndExpenseItem_Expense_Id(5L, 50L, 500L);
            verify(shares).deleteById(5L);
        }

        @Test
        @DisplayName("ไม่มีอยู่ → 404")
        void deleteNotFound() {
            when(shares.existsByIdAndExpenseItem_IdAndExpenseItem_Expense_Id(5L, 50L, 500L))
                    .thenReturn(false);

            ResponseStatusException ex = catchThrowableOfType(
                    () -> service.deleteShareInExpense(500L, 50L, 5L),
                    ResponseStatusException.class
            );
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value());
            assertThat(ex.getReason()).isEqualTo("Share not found in this expense/item");

            verify(shares).existsByIdAndExpenseItem_IdAndExpenseItem_Expense_Id(5L, 50L, 500L);
            verify(shares, never()).deleteById(anyLong());
        }
    }

    // ================================= listByItem (Deprecated) =================================
    @Test
    @DisplayName("[Deprecated] listByItem(itemId): คืน shares ของ item")
    void deprecated_listByItem() {
        Long itemId = 1000L;
        when(shares.findByExpenseItem_Id(itemId)).thenReturn(List.of(
                share(1L, item(itemId, BigDecimal.TEN), user(1L, "x@x"), BigDecimal.ONE, null)
        ));

        List<ExpenseItemShare> result = service.listByItem(itemId);
        assertThat(result).hasSize(1);
        verify(shares).findByExpenseItem_Id(itemId);
    }

    // ================================= addShare (Deprecated) =================================
    @Nested
    @DisplayName("[Deprecated] addShare(itemId, userId, value, percent)")
    class DeprecatedAddShare {

        @Test
        @DisplayName("item+user พบ (percent) → คำนวณจาก amount → save")
        void addPercent_ok() {
            Long itemId = 2000L, userId = 7L;
            ExpenseItem it = item(itemId, new BigDecimal("10.00"));
            User u = user(userId, "y@y");

            when(items.findById(itemId)).thenReturn(Optional.of(it));
            when(users.findById(userId)).thenReturn(Optional.of(u));
            when(shares.save(any(ExpenseItemShare.class))).thenAnswer(inv -> inv.getArgument(0));

            ExpenseItemShare s = service.addShare(itemId, userId, null, new BigDecimal("12.5"));

            // 10 * 12.5% = 1.25 -> scale 2 = 1.25
            assertThat(s.getShareValue()).isEqualByComparingTo("1.25");
            assertThat(s.getSharePercent()).isEqualByComparingTo("12.5");
        }

        @Test
        @DisplayName("item+user พบ (value) → scale 2 → save")
        void addValue_ok() {
            Long itemId = 2001L, userId = 8L;
            when(items.findById(itemId)).thenReturn(Optional.of(item(itemId, new BigDecimal("9.99"))));
            when(users.findById(userId)).thenReturn(Optional.of(user(userId, "z@z")));
            when(shares.save(any(ExpenseItemShare.class))).thenAnswer(inv -> inv.getArgument(0));

            ExpenseItemShare s = service.addShare(itemId, userId, new BigDecimal("2.999"), null);
            assertThat(s.getShareValue()).isEqualByComparingTo("3.00");
            assertThat(s.getSharePercent()).isNull();
        }

        @Test
        @DisplayName("item ไม่พบ → 404")
        void itemNotFound_404() {
            when(items.findById(999L)).thenReturn(Optional.empty());

            ResponseStatusException ex = catchThrowableOfType(
                    () -> service.addShare(999L, 1L, BigDecimal.ONE, null),
                    ResponseStatusException.class
            );
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value());
            assertThat(ex.getReason()).isEqualTo("Expense item not found");
        }

        @Test
        @DisplayName("user ไม่พบ → 404")
        void userNotFound_404() {
            when(items.findById(1L)).thenReturn(Optional.of(item(1L, BigDecimal.ONE)));
            when(users.findById(999L)).thenReturn(Optional.empty());

            ResponseStatusException ex = catchThrowableOfType(
                    () -> service.addShare(1L, 999L, BigDecimal.ONE, null),
                    ResponseStatusException.class
            );
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value());
            assertThat(ex.getReason()).isEqualTo("Participant user not found");
        }
    }

    // ================================= updateShare (Deprecated) =================================
    @Nested
    @DisplayName("[Deprecated] updateShare(shareId, value, percent)")
    class DeprecatedUpdateShare {

        @Test
        @DisplayName("ส่ง percent → คำนวณจาก item.amount → save")
        void updatePercent_ok() {
            Long shareId = 3000L;
            ExpenseItem it = item(77L, new BigDecimal("100.00"));
            ExpenseItemShare s = share(shareId, it, user(9L, "p@x"), new BigDecimal("0.00"), null);

            when(shares.findById(shareId)).thenReturn(Optional.of(s));
            when(shares.save(any(ExpenseItemShare.class))).thenAnswer(inv -> inv.getArgument(0));

            ExpenseItemShare updated = service.updateShare(shareId, null, new BigDecimal("33.333"));

            assertThat(updated.getShareValue()).isEqualByComparingTo("33.33"); // 100 * 33.333% = 33.333 -> 33.33
            assertThat(updated.getSharePercent()).isEqualByComparingTo("33.333");
        }

        @Test
        @DisplayName("ส่ง value → scale 2 → save")
        void updateValue_ok() {
            Long shareId = 3001L;
            ExpenseItemShare s = share(shareId, item(70L, new BigDecimal("50.00")), user(10L, "q@x"), new BigDecimal("1.00"), null);

            when(shares.findById(shareId)).thenReturn(Optional.of(s));
            when(shares.save(any(ExpenseItemShare.class))).thenAnswer(inv -> inv.getArgument(0));

            ExpenseItemShare updated = service.updateShare(shareId, new BigDecimal("2.345"), null);
            assertThat(updated.getShareValue()).isEqualByComparingTo("2.35");
            assertThat(updated.getSharePercent()).isNull();
        }

        @Test
        @DisplayName("share ไม่พบ → 404")
        void updateNotFound() {
            when(shares.findById(999L)).thenReturn(Optional.empty());

            ResponseStatusException ex = catchThrowableOfType(
                    () -> service.updateShare(999L, BigDecimal.ONE, null),
                    ResponseStatusException.class
            );
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value());
            assertThat(ex.getReason()).isEqualTo("Share not found");
        }
    }

    // ================================= listByExpenseAndParticipant =================================
    @Nested
    @DisplayName("listByExpenseAndParticipant(expenseId, userId)")
    class ListByExpenseAndParticipant {

        @Test
        @DisplayName("ok: คืน shares ทั้งหมดของผู้ใช้ใน expense")
        void ok() {
            Long expenseId = 4000L, userId = 40L;
            when(shares.findByExpenseItem_Expense_IdAndParticipant_Id(expenseId, userId))
                    .thenReturn(List.of(
                            share(1L, item(10L, BigDecimal.TEN), user(userId, "u@x"), new BigDecimal("5.00"), null)
                    ));

            List<ExpenseItemShare> result = service.listByExpenseAndParticipant(expenseId, userId);
            assertThat(result).hasSize(1);
            verify(shares).findByExpenseItem_Expense_IdAndParticipant_Id(expenseId, userId);
        }

        @Test
        @DisplayName("expenseId เป็น null → 400 BAD_REQUEST")
        void nullExpenseId_400() {
            ResponseStatusException ex = catchThrowableOfType(
                    () -> service.listByExpenseAndParticipant(null, 1L),
                    ResponseStatusException.class
            );
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value());
            assertThat(ex.getReason()).isEqualTo("expenseId/userId is required");
            verifyNoInteractions(shares);
        }

        @Test
        @DisplayName("userId เป็น null → 400 BAD_REQUEST")
        void nullUserId_400() {
            ResponseStatusException ex = catchThrowableOfType(
                    () -> service.listByExpenseAndParticipant(1L, null),
                    ResponseStatusException.class
            );
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value());
            assertThat(ex.getReason()).isEqualTo("expenseId/userId is required");
            verifyNoInteractions(shares);
        }
    }
}
