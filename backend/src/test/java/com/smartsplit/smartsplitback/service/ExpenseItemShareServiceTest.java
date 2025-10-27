package com.smartsplit.smartsplitback.service;

import com.smartsplit.smartsplitback.model.Expense;
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
import java.util.Map;
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
    @Mock private ExchangeRateService fx;

    @InjectMocks private ExpenseItemShareService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private static Expense makeExpense(Long id) {
        Expense e = new Expense();
        e.setId(id);
        return e;
    }

    private static ExpenseItem item(Long id, BigDecimal amount, String currency, Expense expense) {
        ExpenseItem it = new ExpenseItem();
        it.setId(id);
        it.setAmount(amount);
        it.setCurrency(currency);
        it.setExpense(expense);
        return it;
    }

    private static ExpenseItem item(Long id, BigDecimal amount) {
        return item(id, amount, "THB", makeExpense(999L));
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
        s.setShareOriginalValue(value);
        return s;
    }

    @Nested
    @DisplayName("listByItemInExpense(expenseId, itemId)")
    class ListByItemInExpense {

        @Test
        @DisplayName("item อยู่ใต้ expense → คืนลิสต์ shares ของ item นั้น")
        void ok() {
            Long expenseId = 10L, itemId = 100L;
            when(items.existsByIdAndExpense_Id(itemId, expenseId)).thenReturn(true);

            List<ExpenseItemShare> mock = List.of(
                    share(1L, item(itemId, new BigDecimal("10.00")), user(2L, "a@example.com"), new BigDecimal("3.33"), new BigDecimal("33.33"))
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

    @Nested
    @DisplayName("addShareInExpense(expenseId, itemId, userId, shareValue, sharePercent)")
    class AddShareInExpense {

        @Test
        @DisplayName("percent + THB currency: ใช้เรต THB=1 → shareOriginal=scale2, shareValue=original")
        void addPercent_thb_ok() {
            Long expenseId = 30L, itemId = 300L, userId = 3L;
            Expense exp = makeExpense(expenseId);
            ExpenseItem it = item(itemId, new BigDecimal("100.00"), "THB", exp);
            User u = user(userId, "u@example.com");

            when(items.findByIdAndExpense_Id(itemId, expenseId)).thenReturn(Optional.of(it));
            when(users.findById(userId)).thenReturn(Optional.of(u));

            Map<String, BigDecimal> rates = Map.of("THB", BigDecimal.ONE);
            when(fx.getRatesToThb(exp)).thenReturn(rates);
            when(fx.toThb(eq("THB"), eq(new BigDecimal("12.35")), eq(rates)))
                    .thenReturn(new BigDecimal("12.35"));

            when(shares.save(any(ExpenseItemShare.class))).thenAnswer(inv -> {
                ExpenseItemShare s = inv.getArgument(0);
                s.setId(999L);
                return s;
            });

            ExpenseItemShare created = service.addShareInExpense(
                    expenseId, itemId, userId, null, new BigDecimal("12.345")
            );

            assertThat(created.getId()).isEqualTo(999L);
            assertThat(created.getShareOriginalValue()).isEqualByComparingTo("12.35");
            assertThat(created.getShareValue()).isEqualByComparingTo("12.35");
            assertThat(created.getSharePercent()).isEqualByComparingTo("12.345");

            verify(fx).getRatesToThb(exp);
            verify(fx).toThb(eq("THB"), eq(new BigDecimal("12.35")), eq(rates));
        }

        @Test
        @DisplayName("value + USD currency: ใช้เรต USD→THB 36.25")
        void addValue_usd_ok() {
            Long expenseId = 31L, itemId = 301L, userId = 4L;
            Expense exp = makeExpense(expenseId);
            ExpenseItem it = item(itemId, new BigDecimal("50.00"), "USD", exp);
            User u = user(userId, "u2@example.com");

            when(items.findByIdAndExpense_Id(itemId, expenseId)).thenReturn(Optional.of(it));
            when(users.findById(userId)).thenReturn(Optional.of(u));

            Map<String, BigDecimal> rates = Map.of("USD", new BigDecimal("36.25"), "THB", BigDecimal.ONE);
            when(fx.getRatesToThb(exp)).thenReturn(rates);
            when(fx.toThb(eq("USD"), eq(new BigDecimal("1.23")), eq(rates)))
                    .thenReturn(new BigDecimal("44.58"));

            when(shares.save(any(ExpenseItemShare.class))).thenAnswer(inv -> inv.getArgument(0));

            ExpenseItemShare created = service.addShareInExpense(
                    expenseId, itemId, userId, new BigDecimal("1.234"), null
            );

            assertThat(created.getShareOriginalValue()).isEqualByComparingTo("1.23");
            assertThat(created.getShareValue()).isEqualByComparingTo("44.58");
            assertThat(created.getSharePercent()).isNull();

            verify(fx).getRatesToThb(exp);
            verify(fx).toThb(eq("USD"), eq(new BigDecimal("1.23")), eq(rates));
        }

        @Test
        @DisplayName("currency เป็นตัวพิมพ์เล็ก → safeUpper → เรียก toThb ด้วย USD")
        void addValue_lowercase_currency_uppercased() {
            Long expenseId = 32L, itemId = 302L, userId = 5L;
            Expense exp = makeExpense(expenseId);
            ExpenseItem it = item(itemId, new BigDecimal("10.00"), "usd", exp);
            User u = user(userId, "u3@example.com");

            when(items.findByIdAndExpense_Id(itemId, expenseId)).thenReturn(Optional.of(it));
            when(users.findById(userId)).thenReturn(Optional.of(u));

            Map<String, BigDecimal> rates = Map.of("USD", new BigDecimal("36.00"));
            when(fx.getRatesToThb(exp)).thenReturn(rates);
            when(fx.toThb(eq("USD"), eq(new BigDecimal("2.00")), eq(rates)))
                    .thenReturn(new BigDecimal("72.00"));

            when(shares.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ExpenseItemShare created = service.addShareInExpense(expenseId, itemId, userId, new BigDecimal("2.00"), null);

            assertThat(created.getShareOriginalValue()).isEqualByComparingTo("2.00");
            assertThat(created.getShareValue()).isEqualByComparingTo("72.00");
            verify(fx).toThb(eq("USD"), eq(new BigDecimal("2.00")), eq(rates));
        }

        @Test
        @DisplayName("item ไม่มี currency → default THB")
        void addValue_null_currency_defaults_thb() {
            Long expenseId = 33L, itemId = 303L, userId = 6L;
            Expense exp = makeExpense(expenseId);
            ExpenseItem it = item(itemId, new BigDecimal("10.00"), null, exp);
            User u = user(userId, "u4@example.com");

            when(items.findByIdAndExpense_Id(itemId, expenseId)).thenReturn(Optional.of(it));
            when(users.findById(userId)).thenReturn(Optional.of(u));

            Map<String, BigDecimal> rates = Map.of("THB", BigDecimal.ONE);
            when(fx.getRatesToThb(exp)).thenReturn(rates);
            when(fx.toThb(eq("THB"), eq(new BigDecimal("9.99")), eq(rates)))
                    .thenReturn(new BigDecimal("9.99"));

            when(shares.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ExpenseItemShare created = service.addShareInExpense(expenseId, itemId, userId, new BigDecimal("9.99"), null);

            assertThat(created.getShareValue()).isEqualByComparingTo("9.99");
            verify(fx).toThb(eq("THB"), eq(new BigDecimal("9.99")), eq(rates));
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
            verifyNoInteractions(users, fx, shares);
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
            verifyNoInteractions(fx, shares);
        }
    }

    @Nested
    @DisplayName("updateShareInExpense(expenseId, itemId, shareId, shareValue, sharePercent)")
    class UpdateShareInExpense {

        @Test
        @DisplayName("ส่ง percent + JPY → ใช้เรต JPY→THB 0.25")
        void updateWithPercent_jpy_ok() {
            Long expenseId = 40L, itemId = 400L, shareId = 4L;
            Expense exp = makeExpense(expenseId);
            ExpenseItem it = item(itemId, new BigDecimal("2000.00"), "JPY", exp);
            ExpenseItemShare existing = share(shareId, it, user(9L, "p@x"), new BigDecimal("0"), null);

            when(shares.findByIdAndExpenseItem_IdAndExpenseItem_Expense_Id(shareId, itemId, expenseId))
                    .thenReturn(Optional.of(existing));

            Map<String, BigDecimal> rates = Map.of("JPY", new BigDecimal("0.25"));
            when(fx.getRatesToThb(exp)).thenReturn(rates);

            BigDecimal original = new BigDecimal("666.66"); // 2000 * 33.333% = 666.66 → scale(2) = 666.66
            when(fx.toThb(eq("JPY"), eq(original), eq(rates)))
                    .thenReturn(new BigDecimal("166.66"));

            when(shares.save(any(ExpenseItemShare.class))).thenAnswer(inv -> inv.getArgument(0));

            ExpenseItemShare updated = service.updateShareInExpense(
                    expenseId, itemId, shareId, null, new BigDecimal("33.333")
            );

            assertThat(updated.getShareOriginalValue()).isEqualByComparingTo("666.66");
            assertThat(updated.getShareValue()).isEqualByComparingTo("166.66");
            assertThat(updated.getSharePercent()).isEqualByComparingTo("33.333");

            verify(fx).toThb(eq("JPY"), eq(original), eq(rates));
            verify(shares).save(existing);
        }


        @Test
        @DisplayName("ส่ง value + EUR → เรต 40.00")
        void updateWithValue_eur_ok() {
            Long expenseId = 41L, itemId = 401L, shareId = 5L;
            Expense exp = makeExpense(expenseId);
            ExpenseItem it = item(itemId, new BigDecimal("50.00"), "EUR", exp);
            ExpenseItemShare existing = share(shareId, it, user(9L, "p@x"), null, null);

            when(shares.findByIdAndExpenseItem_IdAndExpenseItem_Expense_Id(shareId, itemId, expenseId))
                    .thenReturn(Optional.of(existing));

            Map<String, BigDecimal> rates = Map.of("EUR", new BigDecimal("40.00"));
            when(fx.getRatesToThb(exp)).thenReturn(rates);

            when(fx.toThb(eq("EUR"), eq(new BigDecimal("2.00")), eq(rates)))
                    .thenReturn(new BigDecimal("80.00"));

            when(shares.save(any(ExpenseItemShare.class))).thenAnswer(inv -> inv.getArgument(0));

            ExpenseItemShare updated = service.updateShareInExpense(
                    expenseId, itemId, shareId, new BigDecimal("1.999"), null
            );

            assertThat(updated.getShareOriginalValue()).isEqualByComparingTo("2.00");
            assertThat(updated.getShareValue()).isEqualByComparingTo("80.00");
            assertThat(updated.getSharePercent()).isNull();
            verify(shares).save(existing);
        }

        @Test
        @DisplayName("ไม่ส่งอะไรเลย → ค่าเดิม แต่คำนวณ THB ใหม่ตามเรต (กรณีเรตเปลี่ยนในระบบ—ถึงแม้ปกติจะล็อก)")
        void updateNoFields_ok() {
            Long expenseId = 42L, itemId = 402L, shareId = 6L;
            Expense exp = makeExpense(expenseId);
            ExpenseItem it = item(itemId, new BigDecimal("10.00"), "THB", exp);
            ExpenseItemShare existing = new ExpenseItemShare();
            existing.setId(shareId);
            existing.setExpenseItem(it);
            existing.setParticipant(user(9L, "p@x"));
            existing.setShareOriginalValue(new BigDecimal("3.00"));
            existing.setShareValue(new BigDecimal("3.00"));
            existing.setSharePercent(new BigDecimal("30"));

            when(shares.findByIdAndExpenseItem_IdAndExpenseItem_Expense_Id(shareId, itemId, expenseId))
                    .thenReturn(Optional.of(existing));

            Map<String, BigDecimal> rates = Map.of("THB", BigDecimal.ONE);
            when(fx.getRatesToThb(exp)).thenReturn(rates);
            when(fx.toThb(eq("THB"), eq(new BigDecimal("3.00")), eq(rates)))
                    .thenReturn(new BigDecimal("3.00"));

            when(shares.save(any(ExpenseItemShare.class))).thenAnswer(inv -> inv.getArgument(0));

            ExpenseItemShare updated = service.updateShareInExpense(expenseId, itemId, shareId, null, null);

            assertThat(updated.getShareOriginalValue()).isEqualByComparingTo("3.00");
            assertThat(updated.getSharePercent()).isEqualByComparingTo("30");
            assertThat(updated.getShareValue()).isEqualByComparingTo("3.00");
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

    @Nested
    @DisplayName("[Deprecated] addShare(itemId, userId, value, percent)")
    class DeprecatedAddShare {

        @Test
        @DisplayName("percent + THB → ค่า original ตามสูตร, toThb THB=1")
        void addPercent_ok() {
            Long itemId = 2000L, userId = 7L;
            Expense exp = makeExpense(7777L);
            ExpenseItem it = item(itemId, new BigDecimal("10.00"), "THB", exp);
            User u = user(userId, "y@y");

            when(items.findById(itemId)).thenReturn(Optional.of(it));
            when(users.findById(userId)).thenReturn(Optional.of(u));

            Map<String, BigDecimal> rates = Map.of("THB", BigDecimal.ONE);
            when(fx.getRatesToThb(exp)).thenReturn(rates);
            when(fx.toThb(eq("THB"), eq(new BigDecimal("1.25")), eq(rates)))
                    .thenReturn(new BigDecimal("1.25"));

            when(shares.save(any(ExpenseItemShare.class))).thenAnswer(inv -> inv.getArgument(0));

            ExpenseItemShare s = service.addShare(itemId, userId, null, new BigDecimal("12.5"));

            assertThat(s.getShareOriginalValue()).isEqualByComparingTo("1.25");
            assertThat(s.getShareValue()).isEqualByComparingTo("1.25");
            assertThat(s.getSharePercent()).isEqualByComparingTo("12.5");
        }

        @Test
        @DisplayName("value + USD → ใช้เรต 36.25")
        void addValue_ok() {
            Long itemId = 2001L, userId = 8L;
            Expense exp = makeExpense(8888L);
            ExpenseItem it = item(itemId, new BigDecimal("9.99"), "USD", exp);
            when(items.findById(itemId)).thenReturn(Optional.of(it));
            when(users.findById(userId)).thenReturn(Optional.of(user(userId, "z@z")));

            Map<String, BigDecimal> rates = Map.of("USD", new BigDecimal("36.25"));
            when(fx.getRatesToThb(exp)).thenReturn(rates);
            when(fx.toThb(eq("USD"), eq(new BigDecimal("3.00")), eq(rates)))
                    .thenReturn(new BigDecimal("108.75"));

            when(shares.save(any(ExpenseItemShare.class))).thenAnswer(inv -> inv.getArgument(0));

            ExpenseItemShare s = service.addShare(itemId, userId, new BigDecimal("2.999"), null);
            assertThat(s.getShareOriginalValue()).isEqualByComparingTo("3.00");
            assertThat(s.getShareValue()).isEqualByComparingTo("108.75");
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
            Expense exp = makeExpense(1L);
            ExpenseItem it = item(1L, BigDecimal.ONE, "THB", exp);
            when(items.findById(1L)).thenReturn(Optional.of(it));
            when(users.findById(999L)).thenReturn(Optional.empty());

            ResponseStatusException ex = catchThrowableOfType(
                    () -> service.addShare(1L, 999L, BigDecimal.ONE, null),
                    ResponseStatusException.class
            );
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value());
            assertThat(ex.getReason()).isEqualTo("Participant user not found");
        }
    }

    @Nested
    @DisplayName("[Deprecated] updateShare(shareId, value, percent)")
    class DeprecatedUpdateShare {

        @Test
        @DisplayName("percent + THB → คำนวณใหม่และแปลง THB=1")
        void updatePercent_ok() {
            Long shareId = 3000L;
            Expense exp = makeExpense(1L);
            ExpenseItem it = item(77L, new BigDecimal("100.00"), "THB", exp);
            ExpenseItemShare s = share(shareId, it, user(9L, "p@x"), new BigDecimal("0.00"), null);

            when(shares.findById(shareId)).thenReturn(Optional.of(s));
            Map<String, BigDecimal> rates = Map.of("THB", BigDecimal.ONE);
            when(fx.getRatesToThb(exp)).thenReturn(rates);
            when(fx.toThb(eq("THB"), eq(new BigDecimal("33.33")), eq(rates)))
                    .thenReturn(new BigDecimal("33.33"));
            when(shares.save(any(ExpenseItemShare.class))).thenAnswer(inv -> inv.getArgument(0));

            ExpenseItemShare updated = service.updateShare(shareId, null, new BigDecimal("33.333"));

            assertThat(updated.getShareOriginalValue()).isEqualByComparingTo("33.33");
            assertThat(updated.getShareValue()).isEqualByComparingTo("33.33");
            assertThat(updated.getSharePercent()).isEqualByComparingTo("33.333");
        }

        @Test
        @DisplayName("value + JPY → ใช้เรต 0.25")
        void updateValue_ok() {
            Long shareId = 3001L;
            Expense exp = makeExpense(2L);
            ExpenseItem it = item(70L, new BigDecimal("50.00"), "JPY", exp);
            ExpenseItemShare s = share(shareId, it, user(10L, "q@x"), new BigDecimal("1.00"), null);

            when(shares.findById(shareId)).thenReturn(Optional.of(s));
            Map<String, BigDecimal> rates = Map.of("JPY", new BigDecimal("0.25"));
            when(fx.getRatesToThb(exp)).thenReturn(rates);
            when(fx.toThb(eq("JPY"), eq(new BigDecimal("2.35")), eq(rates)))
                    .thenReturn(new BigDecimal("0.59"));
            when(shares.save(any(ExpenseItemShare.class))).thenAnswer(inv -> inv.getArgument(0));

            ExpenseItemShare updated = service.updateShare(shareId, new BigDecimal("2.345"), null);
            assertThat(updated.getShareOriginalValue()).isEqualByComparingTo("2.35");
            assertThat(updated.getShareValue()).isEqualByComparingTo("0.59");
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
