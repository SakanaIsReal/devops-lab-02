package com.smartsplit.smartsplitback.service;

import com.smartsplit.smartsplitback.model.dto.BalanceLineDto;
import com.smartsplit.smartsplitback.model.dto.BalanceSummaryDto;
import com.smartsplit.smartsplitback.repository.BalanceQueryRepository;
import com.smartsplit.smartsplitback.repository.BalanceRowProjection;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class UserBalanceServiceTest {

    @Mock
    private BalanceQueryRepository repo;

    @InjectMocks
    private UserBalanceService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // ---------- Test Projection ----------
    private static class Row implements BalanceRowProjection {
        private final String direction;
        private final Long counterpartyUserId;
        private final String counterpartyUserName;
        private final String counterpartyAvatarUrl;
        private final Long groupId;
        private final String groupName;
        private final Long expenseId;
        private final String expenseTitle;
        private final BigDecimal remaining;

        Row(String direction,
            Long counterpartyUserId, String counterpartyUserName, String counterpartyAvatarUrl,
            Long groupId, String groupName,
            Long expenseId, String expenseTitle,
            BigDecimal remaining) {
            this.direction = direction;
            this.counterpartyUserId = counterpartyUserId;
            this.counterpartyUserName = counterpartyUserName;
            this.counterpartyAvatarUrl = counterpartyAvatarUrl;
            this.groupId = groupId;
            this.groupName = groupName;
            this.expenseId = expenseId;
            this.expenseTitle = expenseTitle;
            this.remaining = remaining;
        }

        @Override public String getDirection() { return direction; }
        @Override public Long getCounterpartyUserId() { return counterpartyUserId; }
        @Override public String getCounterpartyUserName() { return counterpartyUserName; }
        @Override public String getCounterpartyAvatarUrl() { return counterpartyAvatarUrl; }
        @Override public Long getGroupId() { return groupId; }
        @Override public String getGroupName() { return groupName; }
        @Override public Long getExpenseId() { return expenseId; }
        @Override public String getExpenseTitle() { return expenseTitle; }
        @Override public BigDecimal getRemaining() { return remaining; }
    }

    // ========================= listBalances(userId) =========================
    @Nested
    @DisplayName("listBalances(userId)")
    class ListBalances {

        @Test
        @DisplayName("แมปทุกฟิลด์จาก projection → DTO ถูกต้อง และคงจำนวนรายการ")
        void map_all_fields_correctly() {
            Long userId = 7L;
            List<BalanceRowProjection> rows = List.of(
                    new Row("YOU_OWE",  2L, "Bob",   "http://a/b.png", 10L, "Trip", 100L, "Hotel",  new BigDecimal("12.34")),
                    new Row("OWES_YOU", 3L, "Carol", "http://a/c.png", 11L, "Food", 101L, "Dinner", new BigDecimal("56.78"))
            );
            when(repo.findBalancesForUser(userId)).thenReturn(rows);

            List<BalanceLineDto> dtos = service.listBalances(userId);

            assertThat(dtos).hasSize(2);

            BalanceLineDto d0 = dtos.get(0);
            assertThat(d0.direction()).isEqualTo("YOU_OWE");
            assertThat(d0.counterpartyUserId()).isEqualTo(2L);
            assertThat(d0.counterpartyUserName()).isEqualTo("Bob");
            assertThat(d0.counterpartyAvatarUrl()).isEqualTo("http://a/b.png");
            assertThat(d0.groupId()).isEqualTo(10L);
            assertThat(d0.groupName()).isEqualTo("Trip");
            assertThat(d0.expenseId()).isEqualTo(100L);
            assertThat(d0.expenseTitle()).isEqualTo("Hotel");
            assertThat(d0.remaining()).isEqualByComparingTo("12.34");

            BalanceLineDto d1 = dtos.get(1);
            assertThat(d1.direction()).isEqualTo("OWES_YOU");
            assertThat(d1.counterpartyUserId()).isEqualTo(3L);
            assertThat(d1.counterpartyUserName()).isEqualTo("Carol");
            assertThat(d1.counterpartyAvatarUrl()).isEqualTo("http://a/c.png");
            assertThat(d1.groupId()).isEqualTo(11L);
            assertThat(d1.groupName()).isEqualTo("Food");
            assertThat(d1.expenseId()).isEqualTo(101L);
            assertThat(d1.expenseTitle()).isEqualTo("Dinner");
            assertThat(d1.remaining()).isEqualByComparingTo("56.78");

            verify(repo).findBalancesForUser(userId);
            verifyNoMoreInteractions(repo);
        }

        @Test
        @DisplayName("ไม่มีข้อมูล → คืนลิสต์ว่าง")
        void empty_list() {
            when(repo.findBalancesForUser(9L)).thenReturn(List.of());

            var dtos = service.listBalances(9L);

            assertThat(dtos).isEmpty();
            verify(repo).findBalancesForUser(9L);
            verifyNoMoreInteractions(repo);
        }

        @Test
        @DisplayName("รองรับกรณี external share: ผู้ใช้ไม่อยู่ในกลุ่ม แต่มีชื่ออยู่ใน item share → ยังคงแมปแถวได้")
        void external_share_rows_are_mapped() {
            Long userId = 88L;
            // จำลองว่าผลลัพธ์มาจากการมีชื่อใน item share อย่างเดียว (ไม่ใช่สมาชิกกลุ่ม)
            // ข้อมูล groupId/groupName ยังมาจาก expense เดิมตาม query ใหม่
            List<BalanceRowProjection> rows = List.of(
                    new Row("YOU_OWE",  999L, "OwnerX", null, 777L, "ExtGroup", 3001L, "ExtExpense-1", new BigDecimal("20.00")),
                    new Row("OWES_YOU", 555L, "FriendY", null, 777L, "ExtGroup", 3002L, "ExtExpense-2", new BigDecimal("40.50"))
            );
            when(repo.findBalancesForUser(userId)).thenReturn(rows);

            var dtos = service.listBalances(userId);

            assertThat(dtos).hasSize(2);
            assertThat(dtos.get(0).direction()).isEqualTo("YOU_OWE");
            assertThat(dtos.get(0).groupId()).isEqualTo(777L);
            assertThat(dtos.get(0).expenseTitle()).isEqualTo("ExtExpense-1");
            assertThat(dtos.get(0).remaining()).isEqualByComparingTo("20.00");

            assertThat(dtos.get(1).direction()).isEqualTo("OWES_YOU");
            assertThat(dtos.get(1).counterpartyUserName()).isEqualTo("FriendY");
            assertThat(dtos.get(1).remaining()).isEqualByComparingTo("40.50");

            verify(repo).findBalancesForUser(userId);
            verifyNoMoreInteractions(repo);
        }
    }

    // ========================= summary(userId) =========================
    @Nested
    @DisplayName("summary(userId)")
    class Summary {

        @Test
        @DisplayName("รวมยอด YOU_OWE และ OWES_YOU แยกฝั่งถูกต้อง (ทศนิยมได้)")
        void sum_both_directions() {
            Long userId = 5L;
            List<BalanceRowProjection> rows = List.of(
                    new Row("YOU_OWE",  2L, "A", null, 1L, "G1", 10L, "E1", new BigDecimal("1.10")),
                    new Row("YOU_OWE",  3L, "B", null, 1L, "G1", 11L, "E2", new BigDecimal("2.20")),
                    new Row("OWES_YOU", 4L, "C", null, 2L, "G2", 12L, "E3", new BigDecimal("3.30")),
                    new Row("OWES_YOU", 5L, "D", null, 2L, "G2", 13L, "E4", new BigDecimal("4.40"))
            );
            when(repo.findBalancesForUser(userId)).thenReturn(rows);

            BalanceSummaryDto sum = service.summary(userId);

            assertThat(sum.youOweTotal()).isEqualByComparingTo("3.30");     // 1.10 + 2.20
            assertThat(sum.youAreOwedTotal()).isEqualByComparingTo("7.70"); // 3.30 + 4.40
            verify(repo).findBalancesForUser(userId);
            verifyNoMoreInteractions(repo);
        }

        @Test
        @DisplayName("มีเฉพาะ YOU_OWE → youAreOwedTotal = 0")
        void only_you_owe() {
            List<BalanceRowProjection> rows = List.of(
                    new Row("YOU_OWE", 2L, "A", null, 1L, "G1", 10L, "E1", new BigDecimal("5.00"))
            );
            when(repo.findBalancesForUser(1L)).thenReturn(rows);

            var sum = service.summary(1L);

            assertThat(sum.youOweTotal()).isEqualByComparingTo("5.00");
            assertThat(sum.youAreOwedTotal()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("มีเฉพาะ OWES_YOU → youOweTotal = 0")
        void only_owes_you() {
            List<BalanceRowProjection> rows = List.of(
                    new Row("OWES_YOU", 2L, "A", null, 1L, "G1", 10L, "E1", new BigDecimal("7.25"))
            );
            when(repo.findBalancesForUser(2L)).thenReturn(rows);

            var sum = service.summary(2L);

            assertThat(sum.youOweTotal()).isEqualByComparingTo("0.00");
            assertThat(sum.youAreOwedTotal()).isEqualByComparingTo("7.25");
        }

        @Test
        @DisplayName("direction อื่นที่ไม่รู้จัก → ถูกเมิน (ไม่รวมในยอด)")
        void unknown_direction_is_ignored() {
            List<BalanceRowProjection> rows = List.of(
                    new Row("UNKNOWN", 9L, "X", null, 99L, "G", 999L, "E", new BigDecimal("123.45"))
            );
            when(repo.findBalancesForUser(3L)).thenReturn(rows);

            var sum = service.summary(3L);

            assertThat(sum.youOweTotal()).isEqualByComparingTo("0.00");
            assertThat(sum.youAreOwedTotal()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("ลิสต์ว่าง → youOweTotal=0, youAreOwedTotal=0")
        void empty_list() {
            when(repo.findBalancesForUser(4L)).thenReturn(List.of());

            var sum = service.summary(4L);

            assertThat(sum.youOweTotal()).isEqualByComparingTo("0.00");
            assertThat(sum.youAreOwedTotal()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("รองรับ external share: แม้ผู้ใช้ไม่อยู่ในกลุ่ม แต่มีชื่อใน item share → รวมยอดตาม direction ได้ปกติ")
        void includes_external_share_in_totals() {
            Long userId = 123L;
            // สมมติผู้ใช้ 123 ไม่ใช่สมาชิกกลุ่ม แต่มี share ใน expense ต่าง ๆ
            List<BalanceRowProjection> rows = List.of(
                    new Row("YOU_OWE",  900L, "OwnerOut", null, 700L, "ExtG", 5000L, "ExtE1", new BigDecimal("10.00")),
                    new Row("OWES_YOU", 800L, "FriendOut", null, 700L, "ExtG", 5001L, "ExtE2", new BigDecimal("25.50"))
            );
            when(repo.findBalancesForUser(userId)).thenReturn(rows);

            BalanceSummaryDto sum = service.summary(userId);

            assertThat(sum.youOweTotal()).isEqualByComparingTo("10.00");
            assertThat(sum.youAreOwedTotal()).isEqualByComparingTo("25.50");
            verify(repo).findBalancesForUser(userId);
            verifyNoMoreInteractions(repo);
        }
    }
}
