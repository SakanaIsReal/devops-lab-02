package com.smartsplit.smartsplitback.service;

import com.smartsplit.smartsplitback.model.Expense;
import com.smartsplit.smartsplitback.model.ExpenseItem;
import com.smartsplit.smartsplitback.repository.ExpenseItemRepository;
import com.smartsplit.smartsplitback.repository.ExpenseRepository;
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

class ExpenseItemServiceTest {

    @Mock private ExpenseItemRepository items;
    @Mock private ExpenseRepository expenses;

    @InjectMocks private ExpenseItemService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private static Expense newExpense(Long id) {
        Expense e = new Expense();
        e.setId(id);
        return e;
    }

    private static ExpenseItem newItem(Long id, Expense e, String name, BigDecimal amount, String currency) {
        ExpenseItem it = new ExpenseItem();
        it.setId(id);
        it.setExpense(e);
        it.setName(name);
        it.setAmount(amount);
        it.setCurrency(currency);
        return it;
    }

    @Nested
    @DisplayName("listByExpense(expenseId)")
    class ListByExpense {

        @Test
        @DisplayName("คืนรายการทั้งหมดของ expense นั้น (กรณีมีรายการ)")
        void returnsItems() {
            Long expenseId = 7L;
            Expense e = newExpense(expenseId);

            List<ExpenseItem> mockList = List.of(
                    newItem(1L, e, "A", new BigDecimal("10.50"), "THB"),
                    newItem(2L, e, "B", new BigDecimal("5.00"), "USD")
            );
            when(items.findByExpense_Id(expenseId)).thenReturn(mockList);

            List<ExpenseItem> result = service.listByExpense(expenseId);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getName()).isEqualTo("A");
            assertThat(result.get(1).getAmount()).isEqualByComparingTo("5.00");
            assertThat(result.get(1).getCurrency()).isEqualTo("USD");

            verify(items).findByExpense_Id(expenseId);
        }

        @Test
        @DisplayName("คืนลิสต์ว่างเมื่อไม่มีรายการ")
        void returnsEmptyList() {
            Long expenseId = 8L;
            when(items.findByExpense_Id(expenseId)).thenReturn(List.of());

            List<ExpenseItem> result = service.listByExpense(expenseId);

            assertThat(result).isEmpty();
            verify(items).findByExpense_Id(expenseId);
        }
    }

    @Nested
    @DisplayName("getInExpense(expenseId, itemId)")
    class GetInExpense {

        @Test
        @DisplayName("พบ item ใน expense -> คืนค่า (not null)")
        void found() {
            Long expenseId = 10L, itemId = 100L;
            Expense e = newExpense(expenseId);
            ExpenseItem it = newItem(itemId, e, "X", new BigDecimal("1.00"), "THB");

            when(items.findByIdAndExpense_Id(itemId, expenseId)).thenReturn(Optional.of(it));

            ExpenseItem result = service.getInExpense(expenseId, itemId);
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(itemId);

            verify(items).findByIdAndExpense_Id(itemId, expenseId);
        }

        @Test
        @DisplayName("ไม่พบ -> คืน null")
        void notFound() {
            Long expenseId = 10L, itemId = 999L;
            when(items.findByIdAndExpense_Id(itemId, expenseId)).thenReturn(Optional.empty());

            ExpenseItem result = service.getInExpense(expenseId, itemId);
            assertThat(result).isNull();

            verify(items).findByIdAndExpense_Id(itemId, expenseId);
        }
    }

    @Nested
    @DisplayName("create(expenseId, name, amount, currency)")
    class CreateItem {

        @Test
        @DisplayName("happy path: expense มีอยู่ -> สร้าง item แล้ว save (กำหนดสกุล)")
        void createSuccess_withCurrency() {
            Long expenseId = 20L;
            Expense e = newExpense(expenseId);

            when(expenses.findById(expenseId)).thenReturn(Optional.of(e));
            doAnswer(inv -> {
                ExpenseItem arg = inv.getArgument(0, ExpenseItem.class);
                arg.setId(111L);
                return arg;
            }).when(items).save(any(ExpenseItem.class));

            ExpenseItem created = service.create(expenseId, "Milk", new BigDecimal("12.34"), "USD");

            assertThat(created.getId()).isEqualTo(111L);
            assertThat(created.getExpense()).isSameAs(e);
            assertThat(created.getName()).isEqualTo("Milk");
            assertThat(created.getAmount()).isEqualByComparingTo("12.34");
            assertThat(created.getCurrency()).isEqualTo("USD");

            verify(expenses).findById(expenseId);
            verify(items).save(argThat(it ->
                    it.getExpense() == e &&
                            "Milk".equals(it.getName()) &&
                            new BigDecimal("12.34").compareTo(it.getAmount()) == 0 &&
                            "USD".equals(it.getCurrency())
            ));
        }

        @Test
        @DisplayName("กรณี currency = THB และค่า boundary: 0, 0.01, 9999999999.99")
        void createAmountBoundaries() {
            Long expenseId = 21L;
            Expense e = newExpense(expenseId);
            when(expenses.findById(expenseId)).thenReturn(Optional.of(e));
            when(items.save(any(ExpenseItem.class))).thenAnswer(inv -> {
                ExpenseItem arg = inv.getArgument(0, ExpenseItem.class);
                if (arg.getId() == null) arg.setId(1L);
                return arg;
            });

            ExpenseItem z = service.create(expenseId, "Z", new BigDecimal("0"), "THB");
            assertThat(z.getAmount()).isEqualByComparingTo("0");
            assertThat(z.getCurrency()).isEqualTo("THB");

            ExpenseItem cent = service.create(expenseId, "C", new BigDecimal("0.01"), "THB");
            assertThat(cent.getAmount()).isEqualByComparingTo("0.01");

            ExpenseItem big = service.create(expenseId, "B", new BigDecimal("9999999999.99"), "THB");
            assertThat(big.getAmount()).isEqualByComparingTo("9999999999.99");

            verify(items, times(3)).save(any(ExpenseItem.class));
        }

        @Test
        @DisplayName("expense ไม่พบ -> 404 NOT_FOUND")
        void expenseNotFound() {
            Long expenseId = 404L;
            when(expenses.findById(expenseId)).thenReturn(Optional.empty());

            ResponseStatusException ex = catchThrowableOfType(
                    () -> service.create(expenseId, "X", new BigDecimal("1.0"), "THB"),
                    ResponseStatusException.class
            );
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value());
            assertThat(ex.getReason()).isEqualTo("Expense not found");

            verify(expenses).findById(expenseId);
            verify(items, never()).save(any());
        }
    }

    @Nested
    @DisplayName("updateInExpense(expenseId, itemId, name, amount, currency)")
    class UpdateInExpense {

        @Test
        @DisplayName("พบ item -> อัปเดตแบบ partial: name, amount, currency ทีละฟิลด์")
        void updatePartialFields() {
            Long expenseId = 30L, itemId = 300L;
            Expense e = newExpense(expenseId);
            ExpenseItem it = newItem(itemId, e, "Old", new BigDecimal("10.00"), "THB");

            when(items.findByIdAndExpense_Id(itemId, expenseId)).thenReturn(Optional.of(it));
            when(items.save(any(ExpenseItem.class))).thenAnswer(inv -> inv.getArgument(0));

            ExpenseItem updated = service.updateInExpense(expenseId, itemId, "NewName", null, null);
            assertThat(updated.getName()).isEqualTo("NewName");
            assertThat(updated.getAmount()).isEqualByComparingTo("10.00");
            assertThat(updated.getCurrency()).isEqualTo("THB");

            updated = service.updateInExpense(expenseId, itemId, null, new BigDecimal("12.50"), null);
            assertThat(updated.getName()).isEqualTo("NewName");
            assertThat(updated.getAmount()).isEqualByComparingTo("12.50");
            assertThat(updated.getCurrency()).isEqualTo("THB");

            updated = service.updateInExpense(expenseId, itemId, null, null, "USD");
            assertThat(updated.getCurrency()).isEqualTo("USD");

            verify(items, times(3)).findByIdAndExpense_Id(itemId, expenseId);
            verify(items, times(3)).save(any(ExpenseItem.class));
        }

        @Test
        @DisplayName("อัปเดตพร้อมกันทุกฟิลด์")
        void updateAllFields() {
            Long expenseId = 31L, itemId = 310L;
            Expense e = newExpense(expenseId);
            ExpenseItem it = newItem(itemId, e, "Old", new BigDecimal("1.00"), "THB");

            when(items.findByIdAndExpense_Id(itemId, expenseId)).thenReturn(Optional.of(it));
            when(items.save(any(ExpenseItem.class))).thenAnswer(inv -> inv.getArgument(0));

            ExpenseItem updated = service.updateInExpense(expenseId, itemId, "ALL", new BigDecimal("2.22"), "EUR");
            assertThat(updated.getName()).isEqualTo("ALL");
            assertThat(updated.getAmount()).isEqualByComparingTo("2.22");
            assertThat(updated.getCurrency()).isEqualTo("EUR");

            verify(items).findByIdAndExpense_Id(itemId, expenseId);
            verify(items).save(any(ExpenseItem.class));
        }

        @Test
        @DisplayName("ไม่พบ item ใน expense -> 404 NOT_FOUND")
        void updateNotFound() {
            Long expenseId = 30L, itemId = 999L;
            when(items.findByIdAndExpense_Id(itemId, expenseId)).thenReturn(Optional.empty());

            ResponseStatusException ex = catchThrowableOfType(
                    () -> service.updateInExpense(expenseId, itemId, "N", new BigDecimal("1.0"), "THB"),
                    ResponseStatusException.class
            );
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value());
            assertThat(ex.getReason()).isEqualTo("Expense item not found in this expense");

            verify(items).findByIdAndExpense_Id(itemId, expenseId);
            verify(items, never()).save(any());
        }
    }

    @Nested
    @DisplayName("deleteInExpense(expenseId, itemId)")
    class DeleteInExpense {

        @Test
        @DisplayName("exists=true -> ลบสำเร็จ (เรียก deleteById)")
        void deleteSuccess() {
            Long expenseId = 40L, itemId = 400L;
            when(items.existsByIdAndExpense_Id(itemId, expenseId)).thenReturn(true);

            service.deleteInExpense(expenseId, itemId);

            verify(items).existsByIdAndExpense_Id(itemId, expenseId);
            verify(items).deleteById(itemId);
        }

        @Test
        @DisplayName("exists=false -> 404 NOT_FOUND")
        void deleteNotFound() {
            Long expenseId = 40L, itemId = 404L;
            when(items.existsByIdAndExpense_Id(itemId, expenseId)).thenReturn(false);

            ResponseStatusException ex = catchThrowableOfType(
                    () -> service.deleteInExpense(expenseId, itemId),
                    ResponseStatusException.class
            );
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value());
            assertThat(ex.getReason()).isEqualTo("Expense item not found in this expense");

            verify(items).existsByIdAndExpense_Id(itemId, expenseId);
            verify(items, never()).deleteById(anyLong());
        }
    }

    @Nested
    @DisplayName("sumItems(expenseId)")
    class SumItems {

        @Test
        @DisplayName("รีเทิร์นผลรวม amount จาก repo")
        void sumOk() {
            Long expenseId = 50L;
            when(items.sumAmountByExpenseId(expenseId)).thenReturn(new BigDecimal("123.45"));

            BigDecimal sum = service.sumItems(expenseId);
            assertThat(sum).isEqualByComparingTo("123.45");

            verify(items).sumAmountByExpenseId(expenseId);
        }

        @Test
        @DisplayName("repo คืน null -> รีเทิร์น 0")
        void sumNullAsZero() {
            Long expenseId = 51L;
            when(items.sumAmountByExpenseId(expenseId)).thenReturn(null);

            BigDecimal sum = service.sumItems(expenseId);
            assertThat(sum).isEqualByComparingTo("0");

            verify(items).sumAmountByExpenseId(expenseId);
        }
    }

    @Nested
    @DisplayName("[Deprecated] get(id)")
    class DeprecatedGet {

        @Test
        @DisplayName("พบ -> คืน item, ไม่พบ -> null")
        void getFoundOrNull() {
            Long id = 60L;
            Expense e = newExpense(6L);
            ExpenseItem it = newItem(id, e, "Dep", new BigDecimal("9.99"), "THB");

            when(items.findById(id)).thenReturn(Optional.of(it));
            when(items.findById(999L)).thenReturn(Optional.empty());

            assertThat(service.get(id)).isSameAs(it);
            assertThat(service.get(999L)).isNull();

            verify(items).findById(id);
            verify(items).findById(999L);
        }
    }

    @Nested
    @DisplayName("[Deprecated] update(itemId, name, amount)")
    class DeprecatedUpdate {

        @Test
        @DisplayName("พบ -> อัปเดตเฉพาะฟิลด์ที่ไม่ null แล้ว save")
        void updateOk() {
            Long itemId = 70L;
            Expense e = newExpense(7L);
            ExpenseItem it = newItem(itemId, e, "Old", new BigDecimal("10.00"), "THB");

            when(items.findById(itemId)).thenReturn(Optional.of(it));
            when(items.save(any(ExpenseItem.class))).thenAnswer(inv -> inv.getArgument(0));

            ExpenseItem updated = service.update(itemId, "New", null);
            assertThat(updated.getName()).isEqualTo("New");
            assertThat(updated.getAmount()).isEqualByComparingTo("10.00");
            assertThat(updated.getCurrency()).isEqualTo("THB");

            updated = service.update(itemId, null, new BigDecimal("15.25"));
            assertThat(updated.getName()).isEqualTo("New");
            assertThat(updated.getAmount()).isEqualByComparingTo("15.25");

            verify(items, times(2)).findById(itemId);
            verify(items, times(2)).save(any(ExpenseItem.class));
        }

        @Test
        @DisplayName("ไม่พบ -> 404 NOT_FOUND")
        void updateNotFound() {
            when(items.findById(999L)).thenReturn(Optional.empty());

            ResponseStatusException ex = catchThrowableOfType(
                    () -> service.update(999L, "X", BigDecimal.ONE),
                    ResponseStatusException.class
            );
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value());
            assertThat(ex.getReason()).isEqualTo("Expense item not found");

            verify(items).findById(999L);
            verify(items, never()).save(any());
        }
    }

    @Nested
    @DisplayName("[Deprecated] delete(itemId)")
    class DeprecatedDelete {

        @Test
        @DisplayName("exists = true -> ลบได้")
        void deleteOk() {
            Long itemId = 80L;
            when(items.existsById(itemId)).thenReturn(true);

            service.delete(itemId);

            verify(items).existsById(itemId);
            verify(items).deleteById(itemId);
        }

        @Test
        @DisplayName("exists = false -> 404 NOT_FOUND")
        void deleteNotFound() {
            Long itemId = 81L;
            when(items.existsById(itemId)).thenReturn(false);

            ResponseStatusException ex = catchThrowableOfType(
                    () -> service.delete(itemId),
                    ResponseStatusException.class
            );
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value());
            assertThat(ex.getReason()).isEqualTo("Expense item not found");

            verify(items).existsById(itemId);
            verify(items, never()).deleteById(anyLong());
        }
    }
}
