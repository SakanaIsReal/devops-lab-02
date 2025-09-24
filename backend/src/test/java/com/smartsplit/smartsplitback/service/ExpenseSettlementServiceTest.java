package com.smartsplit.smartsplitback.service;

import com.smartsplit.smartsplitback.model.ExpenseItem;
import com.smartsplit.smartsplitback.model.ExpenseItemShare;
import com.smartsplit.smartsplitback.repository.ExpenseItemShareRepository;
import com.smartsplit.smartsplitback.repository.ExpensePaymentRepository;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExpenseSettlementServiceTest {

    @Mock private ExpenseItemShareRepository shares;
    @Mock private ExpensePaymentRepository payments;

    @InjectMocks private ExpenseSettlementService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // ---------- Helpers ----------
    private static ExpenseItem item(BigDecimal amount) {
        ExpenseItem it = new ExpenseItem();
        it.setAmount(amount);
        return it;
    }

    private static ExpenseItemShare share(BigDecimal itemAmount, BigDecimal shareValue, BigDecimal sharePercent) {
        ExpenseItemShare s = new ExpenseItemShare();
        s.setExpenseItem(item(itemAmount));
        s.setShareValue(shareValue);
        s.setSharePercent(sharePercent);
        return s;
    }

    // ================================= owedForUser =================================
    @Nested
    @DisplayName("owedForUser(expenseId, userId)")
    class OwedForUser {

        @Test
        @DisplayName("ค่า share คิดจาก value + percent/100 * item.amount รวมทุกบรรทัด")
        void sum_value_plus_percent_of_itemAmount() {
            Long expenseId = 10L, userId = 1L;

            // rows:
            // 1) item=100, value=2.50, percent=10  -> 2.50 + (100*10/100)=10 => 12.50
            // 2) item=50,  value=null, percent=20  -> 0    + (50*20/100)=10  => 10.00
            // 3) item=80,  value=1.25, percent=null-> 1.25 + 0               => 1.25
            // 4) item=null,value=null, percent=null-> 0 + 0                  => 0
            when(shares.fetchForExpenseAndUser(expenseId, userId)).thenReturn(List.of(
                    share(new BigDecimal("100"), new BigDecimal("2.50"), new BigDecimal("10")),
                    share(new BigDecimal("50"), null, new BigDecimal("20")),
                    share(new BigDecimal("80"), new BigDecimal("1.25"), null),
                    share(null, null, null)
            ));

            BigDecimal owed = service.owedForUser(expenseId, userId);

            assertThat(owed).isEqualByComparingTo("23.75"); // 12.50 + 10 + 1.25 + 0
            verify(shares).fetchForExpenseAndUser(expenseId, userId);
        }

        @Test
        @DisplayName("รองรับ null ทุกฟิลด์ (item.amount/value/percent) -> ปฏิบัติเป็นศูนย์ ไม่โยน exception")
        void tolerate_nulls_as_zero() {
            Long expenseId = 11L, userId = 2L;
            when(shares.fetchForExpenseAndUser(expenseId, userId)).thenReturn(List.of(
                    share(null, null, new BigDecimal("10")),   // base = 0
                    share(null, new BigDecimal("1.00"), null), // percent = 0
                    share(null, null, null)                    // all null
            ));

            BigDecimal owed = service.owedForUser(expenseId, userId);

            assertThat(owed).isEqualByComparingTo("1.00");
        }

        @Test
        @DisplayName("ไม่มีรายการ share → เป็น 0")
        void empty_shares_is_zero() {
            when(shares.fetchForExpenseAndUser(99L, 9L)).thenReturn(List.of());

            BigDecimal owed = service.owedForUser(99L, 9L);

            assertThat(owed).isEqualByComparingTo("0");
        }
    }

    // ================================= paidForUser =================================
    @Nested
    @DisplayName("paidForUser(expenseId, userId)")
    class PaidForUser {

        @Test
        @DisplayName("delegate ไป payments.sumVerifiedAmountByExpenseIdAndUser และคืนค่าตามนั้น")
        void delegates_to_repo() {
            when(payments.sumVerifiedAmountByExpenseIdAndUser(10L, 1L))
                    .thenReturn(new BigDecimal("12.34"));

            BigDecimal paid = service.paidForUser(10L, 1L);

            assertThat(paid).isEqualByComparingTo("12.34");
            verify(payments).sumVerifiedAmountByExpenseIdAndUser(10L, 1L);
        }
    }

    // ================================= userSettlement =================================
    @Nested
    @DisplayName("userSettlement(expenseId, userId)")
    class UserSettlement {

        @Test
        @DisplayName("paid >= owed → settled=true, remaining=0")
        void settled_true_and_remaining_zero() {
            Long expenseId = 20L, userId = 2L;
            when(shares.fetchForExpenseAndUser(expenseId, userId)).thenReturn(List.of(
                    share(new BigDecimal("100"), new BigDecimal("5.00"), new BigDecimal("5")) // 5 + 5 = 10
            ));
            when(payments.sumVerifiedAmountByExpenseIdAndUser(expenseId, userId))
                    .thenReturn(new BigDecimal("10.00"));

            var dto = service.userSettlement(expenseId, userId);

            assertThat(dto.expenseId()).isEqualTo(expenseId);
            assertThat(dto.userId()).isEqualTo(userId);
            assertThat(dto.owedAmount()).isEqualByComparingTo("10.00");
            assertThat(dto.paidAmount()).isEqualByComparingTo("10.00");
            assertThat(dto.settled()).isTrue();
            assertThat(dto.remaining()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("paid < owed → settled=false, remaining=owed-paid (ไม่ติดลบ)")
        void settled_false_and_remaining_positive() {
            Long expenseId = 21L, userId = 3L;
            when(shares.fetchForExpenseAndUser(expenseId, userId)).thenReturn(List.of(
                    share(new BigDecimal("50"), null, new BigDecimal("10")) // 0 + 5 = 5
            ));
            when(payments.sumVerifiedAmountByExpenseIdAndUser(expenseId, userId))
                    .thenReturn(new BigDecimal("3.50"));

            var dto = service.userSettlement(expenseId, userId);

            assertThat(dto.owedAmount()).isEqualByComparingTo("5.00");
            assertThat(dto.paidAmount()).isEqualByComparingTo("3.50");
            assertThat(dto.settled()).isFalse();
            assertThat(dto.remaining()).isEqualByComparingTo("1.50");
        }

        @Test
        @DisplayName("paid > owed มาก ๆ → remaining ถูก clamp เป็น 0 (ไม่ติดลบ)")
        void remaining_clamped_to_zero() {
            Long expenseId = 22L, userId = 4L;
            when(shares.fetchForExpenseAndUser(expenseId, userId)).thenReturn(List.of(
                    share(new BigDecimal("10"), new BigDecimal("0.50"), new BigDecimal("0")) // 0.50
            ));
            when(payments.sumVerifiedAmountByExpenseIdAndUser(expenseId, userId))
                    .thenReturn(new BigDecimal("99.99"));

            var dto = service.userSettlement(expenseId, userId);

            assertThat(dto.owedAmount()).isEqualByComparingTo("0.50");
            assertThat(dto.paidAmount()).isEqualByComparingTo("99.99");
            assertThat(dto.settled()).isTrue();
            assertThat(dto.remaining()).isEqualByComparingTo("0.00");
        }
    }

    // ================================= allSettlements =================================
    @Nested
    @DisplayName("allSettlements(expenseId)")
    class AllSettlements {

        @Test
        @DisplayName("รวมรายชื่อ (union) จาก shares และ payments, เรียงตาม userId, map เป็น DTO ทีละ user")
        void union_sorted_and_mapped_to_dto() {
            Long expenseId = 30L;

            // participants จาก shares: 3,1
            when(shares.findParticipantIdsByExpense(expenseId)).thenReturn(List.of(3L, 1L));
            // participants จาก payments VERIFIED: 2,3
            when(payments.findVerifiedPayerIdsByExpense(expenseId)).thenReturn(List.of(2L, 3L));

            // Mock owed/paid ผ่านภายใน userSettlement() โดยโมเดล owedForUser & paidForUser:
            // owed(1)=5, paid(1)=2 -> not settled, remaining=3
            // owed(2)=0, paid(2)=7 -> settled, remaining=0
            // owed(3)=10, paid(3)=10 -> settled, remaining=0
            when(shares.fetchForExpenseAndUser(expenseId, 1L)).thenReturn(List.of(
                    share(new BigDecimal("50"), null, new BigDecimal("10")) // 5.00
            ));
            when(payments.sumVerifiedAmountByExpenseIdAndUser(expenseId, 1L)).thenReturn(new BigDecimal("2.00"));

            when(shares.fetchForExpenseAndUser(expenseId, 2L)).thenReturn(List.of()); // owed = 0
            when(payments.sumVerifiedAmountByExpenseIdAndUser(expenseId, 2L)).thenReturn(new BigDecimal("7.00"));

            when(shares.fetchForExpenseAndUser(expenseId, 3L)).thenReturn(List.of(
                    share(new BigDecimal("100"), null, new BigDecimal("10")) // 10.00
            ));
            when(payments.sumVerifiedAmountByExpenseIdAndUser(expenseId, 3L)).thenReturn(new BigDecimal("10.00"));

            var list = service.allSettlements(expenseId);

            // ต้องได้ userId = [1,2,3] ตามลำดับ
            assertThat(list).hasSize(3);
            assertThat(list.get(0).userId()).isEqualTo(1L);
            assertThat(list.get(0).owedAmount()).isEqualByComparingTo("5.00");
            assertThat(list.get(0).paidAmount()).isEqualByComparingTo("2.00");
            assertThat(list.get(0).settled()).isFalse();
            assertThat(list.get(0).remaining()).isEqualByComparingTo("3.00");

            assertThat(list.get(1).userId()).isEqualTo(2L);
            assertThat(list.get(1).owedAmount()).isEqualByComparingTo("0.00");
            assertThat(list.get(1).paidAmount()).isEqualByComparingTo("7.00");
            assertThat(list.get(1).settled()).isTrue();
            assertThat(list.get(1).remaining()).isEqualByComparingTo("0.00");

            assertThat(list.get(2).userId()).isEqualTo(3L);
            assertThat(list.get(2).owedAmount()).isEqualByComparingTo("10.00");
            assertThat(list.get(2).paidAmount()).isEqualByComparingTo("10.00");
            assertThat(list.get(2).settled()).isTrue();
            assertThat(list.get(2).remaining()).isEqualByComparingTo("0.00");

            // interaction หลัก ๆ
            verify(shares).findParticipantIdsByExpense(expenseId);
            verify(payments).findVerifiedPayerIdsByExpense(expenseId);

            // ตรวจว่ากลุ่มที่รวมกันเป็น [1,2,3] (เรียก owed/paid ของทั้งสาม user)
            verify(shares).fetchForExpenseAndUser(expenseId, 1L);
            verify(shares).fetchForExpenseAndUser(expenseId, 2L);
            verify(shares).fetchForExpenseAndUser(expenseId, 3L);

            verify(payments).sumVerifiedAmountByExpenseIdAndUser(expenseId, 1L);
            verify(payments).sumVerifiedAmountByExpenseIdAndUser(expenseId, 2L);
            verify(payments).sumVerifiedAmountByExpenseIdAndUser(expenseId, 3L);
        }

        @Test
        @DisplayName("ไม่มีทั้ง shares และ payments → คืนลิสต์ว่าง")
        void returns_empty_when_no_participants() {
            Long expenseId = 31L;
            when(shares.findParticipantIdsByExpense(expenseId)).thenReturn(List.of());
            when(payments.findVerifiedPayerIdsByExpense(expenseId)).thenReturn(List.of());

            var list = service.allSettlements(expenseId);

            assertThat(list).isEmpty();
            verify(shares).findParticipantIdsByExpense(expenseId);
            verify(payments).findVerifiedPayerIdsByExpense(expenseId);
            verifyNoMoreInteractions(shares, payments);
        }
    }
}
