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

    // ===== Helpers =====
    private static Expense newExpense(Long id) {
        Expense e = new Expense();
        e.setId(id);
        return e;
    }

    private static ExpenseItem newItem(Long id, Expense e, String name, BigDecimal amount) {
        ExpenseItem it = new ExpenseItem();
        it.setId(id);
        it.setExpense(e);
        it.setName(name);
        it.setAmount(amount);
        return it;
    }

    // =============================== listByExpense ===============================
    @Nested
    @DisplayName("listByExpense(expenseId)")
    class ListByExpense {

        @Test
        @DisplayName("คืนรายการทั้งหมดของ expense นั้น (กรณีมีรายการ)")
        void returnsItems() {
            Long expenseId = 7L;
            Expense e = newExpense(expenseId);

            List<ExpenseItem> mockList = List.of(
                    newItem(1L, e, "A", new BigDecimal("10.50")),
                    newItem(2L, e, "B", new BigDecimal("5.00"))
            );
            when(items.findByExpense_Id(expenseId)).thenReturn(mockList);

            List<ExpenseItem> result = service.listByExpense(expenseId);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getName()).isEqualTo("A");
            assertThat(result.get(1).getAmount()).isEqualByComparingTo("5.00");

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

    // =============================== getInExpense ===============================
    @Nested
    @DisplayName("getInExpense(expenseId, itemId)")
    class GetInExpense {

        @Test
        @DisplayName("พบ item ใน expense -> คืนค่า (not null)")
        void found() {
            Long expenseId = 10L, itemId = 100L;
            Expense e = newExpense(expenseId);
            ExpenseItem it = newItem(itemId, e, "X", new BigDecimal("1.00"));

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

    // =============================== create ===============================
    @Nested
    @DisplayName("create(expenseId, name, amount)")
    class CreateItem {

        @Test
        @DisplayName("happy path: expense มีอยู่ -> สร้าง item แล้ว save")
        void createSuccess() {
            Long expenseId = 20L;
            Expense e = newExpense(expenseId);

            when(expenses.findById(expenseId)).thenReturn(Optional.of(e));
            doAnswer(inv -> {
                ExpenseItem arg = inv.getArgument(0, ExpenseItem.class);
                arg.setId(111L);
                return arg;
            }).when(items).save(any(ExpenseItem.class));

            ExpenseItem created = service.create(expenseId, "Milk", new BigDecimal("12.34"));

            assertThat(created.getId()).isEqualTo(111L);
            assertThat(created.getExpense()).isSameAs(e);
            assertThat(created.getName()).isEqualTo("Milk");
            assertThat(created.getAmount()).isEqualByComparingTo("12.34");

            verify(expenses).findById(expenseId);
            verify(items).save(argThat(it ->
                    it.getExpense() == e &&
                            "Milk".equals(it.getName()) &&
                            new BigDecimal("12.34").compareTo(it.getAmount()) == 0
            ));
        }

        @Test
        @DisplayName("expense ไม่พบ -> 404 NOT_FOUND")
        void expenseNotFound() {
            Long expenseId = 404L;
            when(expenses.findById(expenseId)).thenReturn(Optional.empty());

            ResponseStatusException ex = catchThrowableOfType(
                    () -> service.create(expenseId, "X", new BigDecimal("1.0")),
                    ResponseStatusException.class
            );
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value());
            assertThat(ex.getReason()).isEqualTo("Expense not found");

            verify(expenses).findById(expenseId);
            verify(items, never()).save(any());
        }
    }

    // =============================== updateInExpense ===============================
    @Nested
    @DisplayName("updateInExpense(expenseId, itemId, name, amount)")
    class UpdateInExpense {

        @Test
        @DisplayName("happy path: พบ item ใน expense -> อัปเดตเฉพาะฟิลด์ที่ไม่ null แล้ว save")
        void updateSuccess_partialFields() {
            Long expenseId = 30L, itemId = 300L;
            Expense e = newExpense(expenseId);
            ExpenseItem it = newItem(itemId, e, "Old", new BigDecimal("10.00"));

            when(items.findByIdAndExpense_Id(itemId, expenseId)).thenReturn(Optional.of(it));
            when(items.save(any(ExpenseItem.class))).thenAnswer(inv -> inv.getArgument(0));

            // อัปเดตเฉพาะ name (amount เป็น null)
            ExpenseItem updated = service.updateInExpense(expenseId, itemId, "NewName", null);

            assertThat(updated.getName()).isEqualTo("NewName");
            assertThat(updated.getAmount()).isEqualByComparingTo("10.00");

            // อัปเดตเฉพาะ amount (name เป็น null)
            updated = service.updateInExpense(expenseId, itemId, null, new BigDecimal("12.50"));
            assertThat(updated.getName()).isEqualTo("NewName");
            assertThat(updated.getAmount()).isEqualByComparingTo("12.50");

            verify(items, times(2)).findByIdAndExpense_Id(itemId, expenseId);
            verify(items, times(2)).save(any(ExpenseItem.class));
        }

        @Test
        @DisplayName("ไม่พบ item ใน expense -> 404 NOT_FOUND")
        void updateNotFound() {
            Long expenseId = 30L, itemId = 999L;
            when(items.findByIdAndExpense_Id(itemId, expenseId)).thenReturn(Optional.empty());

            ResponseStatusException ex = catchThrowableOfType(
                    () -> service.updateInExpense(expenseId, itemId, "N", new BigDecimal("1.0")),
                    ResponseStatusException.class
            );
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value());
            assertThat(ex.getReason()).isEqualTo("Expense item not found in this expense");

            verify(items).findByIdAndExpense_Id(itemId, expenseId);
            verify(items, never()).save(any());
        }
    }

    // =============================== deleteInExpense ===============================
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

    // =============================== sumItems ===============================
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
    }

    // =============================== Deprecated methods ===============================
    @Nested
    @DisplayName("[Deprecated] get(id)")
    class DeprecatedGet {

        @Test
        @DisplayName("พบ -> คืน item, ไม่พบ -> null")
        void getFoundOrNull() {
            Long id = 60L;
            Expense e = newExpense(6L);
            ExpenseItem it = newItem(id, e, "Dep", new BigDecimal("9.99"));

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
            ExpenseItem it = newItem(itemId, e, "Old", new BigDecimal("10.00"));

            when(items.findById(itemId)).thenReturn(Optional.of(it));
            when(items.save(any(ExpenseItem.class))).thenAnswer(inv -> inv.getArgument(0));

            // เปลี่ยนชื่อเท่านั้น
            ExpenseItem updated = service.update(itemId, "New", null);
            assertThat(updated.getName()).isEqualTo("New");
            assertThat(updated.getAmount()).isEqualByComparingTo("10.00");

            // เปลี่ยนจำนวนเท่านั้น
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
