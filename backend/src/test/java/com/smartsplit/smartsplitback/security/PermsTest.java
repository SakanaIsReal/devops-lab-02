package com.smartsplit.smartsplitback.security;

import com.smartsplit.smartsplitback.repository.*;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Perms: unit tests (ISP + Software QA style)")
class PermsTest {

    @Mock SecurityFacade sec;
    @Mock GroupRepository groups;
    @Mock GroupMemberRepository members;
    @Mock ExpenseRepository expenses;
    @Mock ExpenseItemShareRepository shares;
    @Mock ExpensePaymentRepository payments;
    @Mock ExpenseItemRepository expenseItems;

    private Perms perms;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        perms = new Perms(sec, groups, members, expenses, shares, payments, expenseItems);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
        mocks.close();
    }

    // --------- helpers for currentUserId() ----------
    static class PrincipalWith {
        Long id; Long uid; Long userId; String name;
        Object build() {
            return new Object() {
                public Long getId(){ return id; }
                public Long getUid(){ return uid; }
                public Long getUserId(){ return userId; }
                @Override public String toString(){ return "P"; }
            };
        }
    }
    private void setAuth(Object principal, String name) {
        var auth = new UsernamePasswordAuthenticationToken(principal, null, null);
        // setName ผ่าน principal.toString() ไม่ได้ใน UsernamePasswordAuthenticationToken
        // แต่ Perms.extractUserId ใช้ auth.getName(), ซึ่งคือ principal.toString() โดยดีฟอลต์
        // ดังนั้นถ้าต้องทดสอบ fallback เป็นตัวเลข ให้ principal เป็น String เช่น "123"
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // =========================================================================================
    @Nested
    @DisplayName("currentUserId() / extractUserId()")
    class CurrentUserIdTests {

        @Test
        @DisplayName("ไม่มี Authentication → 401 UNAUTHORIZED")
        void noAuth_401() {
            SecurityContextHolder.clearContext();
            assertThatThrownBy(() -> perms.currentUserId())
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                            .isEqualTo(HttpStatus.UNAUTHORIZED.value()));
        }

        @Test
        @DisplayName("principal มียูนิตเมธอด getId() → คืนค่าตามนั้น")
        void via_getId() {
            var p = new PrincipalWith(); p.id = 123L;
            setAuth(p.build(), null);
            assertThat(perms.currentUserId()).isEqualTo(123L);
        }

        @Test
        @DisplayName("principal มียูนิตเมธอด getUid() → คืนค่าตามนั้น")
        void via_getUid() {
            var p = new PrincipalWith(); p.uid = 44L;
            setAuth(p.build(), null);
            assertThat(perms.currentUserId()).isEqualTo(44L);
        }

        @Test
        @DisplayName("principal มียูนิตเมธอด getUserId() → คืนค่าตามนั้น")
        void via_getUserId() {
            var p = new PrincipalWith(); p.userId = 777L;
            setAuth(p.build(), null);
            assertThat(perms.currentUserId()).isEqualTo(777L);
        }

        @Test
        @DisplayName("ไม่มีเมธอดยอดฮิต → fallback ใช้ auth.getName() เป็นตัวเลข")
        void fallback_authName_numeric() {
            // ใช้ principal เป็นสตริงตัวเลข เพื่อให้ getName() เป็น "999"
            setAuth("999", null);
            assertThat(perms.currentUserId()).isEqualTo(999L);
        }

        @Test
        @DisplayName("fallback ล้มเหลว (name ไม่ใช่เลข) → 401 UNAUTHORIZED")
        void fallback_nonNumeric_401() {
            setAuth("abc", null);
            assertThatThrownBy(() -> perms.currentUserId())
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                            .isEqualTo(HttpStatus.UNAUTHORIZED.value()));
        }
    }

    // =========================================================================================
    @Nested
    @DisplayName("canViewUser(targetUserId)")
    class CanViewUser {

        @Test
        @DisplayName("admin → true")
        void admin_true() {
            when(sec.currentUserId()).thenReturn(1L);
            when(sec.isAdmin()).thenReturn(true);
            assertThat(perms.canViewUser(7L)).isTrue();
            verifyNoInteractions(members);
        }

        @Test
        @DisplayName("เป็นตัวเอง → true")
        void self_true() {
            when(sec.currentUserId()).thenReturn(7L);
            when(sec.isAdmin()).thenReturn(false);
            assertThat(perms.canViewUser(7L)).isTrue();
        }

        @Test
        @DisplayName("เป็นสมาชิกกลุ่มเดียวกัน → true")
        void sharedGroup_true() {
            when(sec.currentUserId()).thenReturn(1L);
            when(sec.isAdmin()).thenReturn(false);
            when(members.existsSharedGroup(1L, 8L)).thenReturn(true);
            assertThat(perms.canViewUser(8L)).isTrue();
        }

        @Test
        @DisplayName("ไม่ใช่แอดมิน ไม่ใช่ตัวเอง ไม่แชร์กลุ่ม → false")
        void no_admin_not_self_not_sharedGroup_false() {
            when(sec.currentUserId()).thenReturn(1L);
            when(sec.isAdmin()).thenReturn(false);
            when(members.existsSharedGroup(1L, 8L)).thenReturn(false);
            assertThat(perms.canViewUser(8L)).isFalse();
        }
    }

    // =========================================================================================
    @Nested
    @DisplayName("isGroupMember(groupId)")
    class IsGroupMember {

        @Test
        @DisplayName("admin → true")
        void admin_true() {
            when(sec.isAdmin()).thenReturn(true);
            assertThat(perms.isGroupMember(10L)).isTrue();
        }

        @Test
        @DisplayName("เป็น owner → true")
        void owner_true() {
            when(sec.isAdmin()).thenReturn(false);
            when(sec.currentUserId()).thenReturn(5L);
            when(groups.findOwnerIdById(10L)).thenReturn(5L);
            assertThat(perms.isGroupMember(10L)).isTrue();
        }

        @Test
        @DisplayName("สมาชิกปกติในกลุ่ม → true")
        void regular_member_true() {
            when(sec.isAdmin()).thenReturn(false);
            when(sec.currentUserId()).thenReturn(5L);
            when(groups.findOwnerIdById(10L)).thenReturn(99L);
            when(members.existsByGroup_IdAndUser_Id(10L, 5L)).thenReturn(true);
            assertThat(perms.isGroupMember(10L)).isTrue();
        }

        @Test
        @DisplayName("ไม่เป็น owner และไม่พบในกลุ่ม → false")
        void not_owner_not_member_false() {
            when(sec.isAdmin()).thenReturn(false);
            when(sec.currentUserId()).thenReturn(5L);
            when(groups.findOwnerIdById(10L)).thenReturn(99L);
            when(members.existsByGroup_IdAndUser_Id(10L, 5L)).thenReturn(false);
            assertThat(perms.isGroupMember(10L)).isFalse();
        }

        @Test
        @DisplayName("เรียก repo แล้ว throw → false (ถูกจับกลืน)")
        void repository_throws_returnsFalse() {
            when(sec.isAdmin()).thenReturn(false);
            when(sec.currentUserId()).thenReturn(5L);
            when(groups.findOwnerIdById(10L)).thenReturn(99L);
            when(members.existsByGroup_IdAndUser_Id(10L, 5L)).thenThrow(new RuntimeException("x"));
            assertThat(perms.isGroupMember(10L)).isFalse();
        }
    }

    // =========================================================================================
    @Nested
    @DisplayName("isGroupOwner / canManageMembers / canManageGroup / canCreateExpenseInGroup")
    class GroupManage {

        @Test
        @DisplayName("isGroupOwner: admin → true")
        void isOwner_admin_true() {
            when(sec.isAdmin()).thenReturn(true);
            assertThat(perms.isGroupOwner(10L)).isTrue();
        }

        @Test
        @DisplayName("isGroupOwner: ผู้ใช้ตรงกับ owner id → true")
        void isOwner_true() {
            when(sec.isAdmin()).thenReturn(false);
            when(sec.currentUserId()).thenReturn(5L);
            when(groups.findOwnerIdById(10L)).thenReturn(5L);
            assertThat(perms.isGroupOwner(10L)).isTrue();
        }

        @Test
        @DisplayName("isGroupOwner: ไม่ใช่ owner → false")
        void isOwner_false() {
            when(sec.isAdmin()).thenReturn(false);
            when(sec.currentUserId()).thenReturn(5L);
            when(groups.findOwnerIdById(10L)).thenReturn(8L);
            assertThat(perms.isGroupOwner(10L)).isFalse();
        }

        @Test
        @DisplayName("canManageMembers/Group: เป็น admin หรือ owner → true")
        void canManage_true() {
            when(sec.isAdmin()).thenReturn(false);
            when(sec.currentUserId()).thenReturn(5L);
            when(groups.findOwnerIdById(10L)).thenReturn(5L);
            assertThat(perms.canManageMembers(10L)).isTrue();
            assertThat(perms.canManageGroup(10L)).isTrue();
        }

        @Test
        @DisplayName("canCreateExpenseInGroup: เป็นสมาชิกกลุ่ม → true")
        void createExpense_member_true() {
            when(sec.isAdmin()).thenReturn(false);
            when(sec.currentUserId()).thenReturn(5L);
            when(groups.findOwnerIdById(10L)).thenReturn(99L);
            when(members.existsByGroup_IdAndUser_Id(10L, 5L)).thenReturn(true);
            assertThat(perms.canCreateExpenseInGroup(10L)).isTrue();
        }
    }

    // =========================================================================================
    @Nested
    @DisplayName("canViewExpense / canManageExpense")
    class ExpenseAccess {

        @Test
        @DisplayName("canViewExpense: admin → true")
        void view_admin_true() {
            when(sec.isAdmin()).thenReturn(true);
            assertThat(perms.canViewExpense(100L)).isTrue();
        }

        @Test
        @DisplayName("canViewExpense: groupId null → false")
        void view_noGroup_false() {
            when(sec.isAdmin()).thenReturn(false);
            when(expenses.findGroupIdByExpenseId(100L)).thenReturn(null);
            assertThat(perms.canViewExpense(100L)).isFalse();
        }

        @Test
        @DisplayName("canViewExpense: เป็นสมาชิกของ group → true")
        void view_member_true() {
            when(sec.isAdmin()).thenReturn(false);
            when(expenses.findGroupIdByExpenseId(100L)).thenReturn(10L);
            when(sec.currentUserId()).thenReturn(5L);
            when(groups.findOwnerIdById(10L)).thenReturn(99L);
            when(members.existsByGroup_IdAndUser_Id(10L, 5L)).thenReturn(true);
            assertThat(perms.canViewExpense(100L)).isTrue();
        }

        @Test
        @DisplayName("canManageExpense: admin → true")
        void manage_admin_true() {
            when(sec.isAdmin()).thenReturn(true);
            assertThat(perms.canManageExpense(100L)).isTrue();
        }

        @Test
        @DisplayName("canManageExpense: group ไม่พบ → false")
        void manage_noGroup_false() {
            when(sec.isAdmin()).thenReturn(false);
            when(sec.currentUserId()).thenReturn(5L);
            when(expenses.findGroupIdByExpenseId(100L)).thenReturn(null);
            assertThat(perms.canManageExpense(100L)).isFalse();
        }

        @Test
        @DisplayName("canManageExpense: owner กลุ่ม → true")
        void manage_owner_true() {
            when(sec.isAdmin()).thenReturn(false);
            when(sec.currentUserId()).thenReturn(5L);
            when(expenses.findGroupIdByExpenseId(100L)).thenReturn(10L);
            when(groups.findOwnerIdById(10L)).thenReturn(5L);
            assertThat(perms.canManageExpense(100L)).isTrue();
        }

        @Test
        @DisplayName("canManageExpense: payer ของ expense → true")
        void manage_payer_true() {
            when(sec.isAdmin()).thenReturn(false);
            when(sec.currentUserId()).thenReturn(7L);
            when(expenses.findGroupIdByExpenseId(100L)).thenReturn(10L);
            when(groups.findOwnerIdById(10L)).thenReturn(5L);
            when(expenses.findPayerUserIdByExpenseId(100L)).thenReturn(7L);
            assertThat(perms.canManageExpense(100L)).isTrue();
        }

        @Test
        @DisplayName("canManageExpense: ไม่ใช่ owner/payer → false")
        void manage_not_owner_or_payer_false() {
            when(sec.isAdmin()).thenReturn(false);
            when(sec.currentUserId()).thenReturn(7L);
            when(expenses.findGroupIdByExpenseId(100L)).thenReturn(10L);
            when(groups.findOwnerIdById(10L)).thenReturn(5L);
            when(expenses.findPayerUserIdByExpenseId(100L)).thenReturn(8L);
            assertThat(perms.canManageExpense(100L)).isFalse();
        }
    }

    // =========================================================================================
    @Nested
    @DisplayName("canSubmitPayment / canViewExpensePayment / canManageExpensePayment")
    class PaymentAccess {

        @Test
        @DisplayName("canSubmitPayment: admin → true")
        void submit_admin_true() {
            when(sec.isAdmin()).thenReturn(true);
            assertThat(perms.canSubmitPayment(100L, 1L)).isTrue();
        }

        @Test
        @DisplayName("canSubmitPayment: fromUserId ต้องตรงกับ current user → ไม่ตรง = false")
        void submit_not_self_false() {
            when(sec.isAdmin()).thenReturn(false);
            when(sec.currentUserId()).thenReturn(5L);
            assertThat(perms.canSubmitPayment(100L, 9L)).isFalse();
        }

        @Test
        @DisplayName("canSubmitPayment: เป็นสมาชิกกลุ่ม + มี share ใน expense → true")
        void submit_member_with_share_true() {
            when(sec.isAdmin()).thenReturn(false);
            when(sec.currentUserId()).thenReturn(5L);
            when(expenses.findGroupIdByExpenseId(100L)).thenReturn(10L);
            when(groups.findOwnerIdById(10L)).thenReturn(99L);
            when(members.existsByGroup_IdAndUser_Id(10L, 5L)).thenReturn(true);
            when(shares.existsByExpenseItem_Expense_IdAndParticipant_Id(100L, 5L)).thenReturn(true);
            assertThat(perms.canSubmitPayment(100L, 5L)).isTrue();
        }

        @Test
        @DisplayName("canSubmitPayment: ไม่มี share ใน expense → false")
        void submit_no_share_false() {
            when(sec.isAdmin()).thenReturn(false);
            when(sec.currentUserId()).thenReturn(5L);
            when(expenses.findGroupIdByExpenseId(100L)).thenReturn(10L);
            when(groups.findOwnerIdById(10L)).thenReturn(99L);
            when(members.existsByGroup_IdAndUser_Id(10L, 5L)).thenReturn(true);
            when(shares.existsByExpenseItem_Expense_IdAndParticipant_Id(100L, 5L)).thenReturn(false);
            assertThat(perms.canSubmitPayment(100L, 5L)).isFalse();
        }

        @Test
        @DisplayName("paymentBelongsToExpense: ตรงคู่ (expenseId, paymentId) → true")
        void belongs_true() {
            when(payments.existsByIdAndExpense_Id(55L, 9L)).thenReturn(true);
            assertThat(perms.paymentBelongsToExpense(9L, 55L)).isTrue();
        }

        @Test
        @DisplayName("paymentBelongsToExpense: พารามิเตอร์ null → false")
        void belongs_null_false() {
            assertThat(perms.paymentBelongsToExpense(null, 1L)).isFalse();
            assertThat(perms.paymentBelongsToExpense(1L, null)).isFalse();
        }

        @Test
        @DisplayName("canViewExpensePayment: ไม่ belong → false")
        void viewPayment_notBelong_false() {
            when(payments.existsByIdAndExpense_Id(55L, 9L)).thenReturn(false);
            assertThat(perms.canViewExpensePayment(9L, 55L)).isFalse();
        }

        @Test
        @DisplayName("canViewExpensePayment: belong + admin → true")
        void viewPayment_belong_admin_true() {
            when(payments.existsByIdAndExpense_Id(55L, 9L)).thenReturn(true);
            when(sec.isAdmin()).thenReturn(true);
            assertThat(perms.canViewExpensePayment(9L, 55L)).isTrue();
        }

        @Test
        @DisplayName("canViewExpensePayment: belong + เป็นสมาชิกกลุ่ม → true")
        void viewPayment_belong_member_true() {
            when(payments.existsByIdAndExpense_Id(55L, 9L)).thenReturn(true);
            when(sec.isAdmin()).thenReturn(false);
            when(expenses.findGroupIdByExpenseId(9L)).thenReturn(10L);
            when(sec.currentUserId()).thenReturn(5L);
            when(groups.findOwnerIdById(10L)).thenReturn(99L);
            when(members.existsByGroup_IdAndUser_Id(10L, 5L)).thenReturn(true);
            assertThat(perms.canViewExpensePayment(9L, 55L)).isTrue();
        }

        @Test
        @DisplayName("canManageExpensePayment: ไม่ belong → false")
        void managePayment_notBelong_false() {
            when(payments.existsByIdAndExpense_Id(55L, 9L)).thenReturn(false);
            assertThat(perms.canManageExpensePayment(9L, 55L)).isFalse();
        }

        @Test
        @DisplayName("canManageExpensePayment: belong + สิทธิ์ manage expense → true")
        void managePayment_belong_and_manageExpense_true() {
            when(payments.existsByIdAndExpense_Id(55L, 9L)).thenReturn(true);
            // canManageExpense(9L) → admin true
            when(sec.isAdmin()).thenReturn(true);
            assertThat(perms.canManageExpensePayment(9L, 55L)).isTrue();
        }
    }

    // =========================================================================================
    @Nested
    @DisplayName("itemBelongsToExpense / canViewExpenseItem / canManageExpenseItem")
    class ItemAccess {

        @Test
        @DisplayName("itemBelongsToExpense: (expense,item) ตรง → true")
        void belongs_true() {
            when(expenseItems.existsByIdAndExpense_Id(1000L, 9L)).thenReturn(true);
            assertThat(perms.itemBelongsToExpense(9L, 1000L)).isTrue();
        }

        @Test
        @DisplayName("itemBelongsToExpense: null ใด ๆ → false")
        void belongs_null_false() {
            assertThat(perms.itemBelongsToExpense(null, 1L)).isFalse();
            assertThat(perms.itemBelongsToExpense(1L, null)).isFalse();
        }

        @Test
        @DisplayName("canViewExpenseItem: ไม่ belong → false")
        void viewItem_notBelong_false() {
            when(expenseItems.existsByIdAndExpense_Id(1000L, 9L)).thenReturn(false);
            assertThat(perms.canViewExpenseItem(9L, 1000L)).isFalse();
        }

        @Test
        @DisplayName("canViewExpenseItem: belong + admin → true")
        void viewItem_belong_admin_true() {
            when(expenseItems.existsByIdAndExpense_Id(1000L, 9L)).thenReturn(true);
            when(sec.isAdmin()).thenReturn(true);
            assertThat(perms.canViewExpenseItem(9L, 1000L)).isTrue();
        }

        @Test
        @DisplayName("canViewExpenseItem: belong + เป็นสมาชิกกลุ่ม → true")
        void viewItem_belong_member_true() {
            when(expenseItems.existsByIdAndExpense_Id(1000L, 9L)).thenReturn(true);
            when(sec.isAdmin()).thenReturn(false);
            when(expenses.findGroupIdByExpenseId(9L)).thenReturn(10L);
            when(sec.currentUserId()).thenReturn(5L);
            when(groups.findOwnerIdById(10L)).thenReturn(99L);
            when(members.existsByGroup_IdAndUser_Id(10L, 5L)).thenReturn(true);
            assertThat(perms.canViewExpenseItem(9L, 1000L)).isTrue();
        }

        @Test
        @DisplayName("canManageExpenseItem: belong + canManageExpense → true")
        void manageItem_belong_and_manageExpense_true() {
            when(expenseItems.existsByIdAndExpense_Id(1000L, 9L)).thenReturn(true);
            // canManageExpense(9L) → admin true
            when(sec.isAdmin()).thenReturn(true);
            assertThat(perms.canManageExpenseItem(9L, 1000L)).isTrue();
        }
    }

    // =========================================================================================
    @Nested
    @DisplayName("shareBelongsToItemInExpense / canManageExpenseShare")
    class ShareAccess {

        @Test
        @DisplayName("shareBelongsToItemInExpense: ทั้งสามค่า null/บางค่า null → false")
        void belongs_null_false() {
            assertThat(perms.shareBelongsToItemInExpense(null, 1L, 2L)).isFalse();
            assertThat(perms.shareBelongsToItemInExpense(1L, null, 2L)).isFalse();
            assertThat(perms.shareBelongsToItemInExpense(1L, 2L, null)).isFalse();
        }

        @Test
        @DisplayName("shareBelongsToItemInExpense: repo ตอบ true → true")
        void belongs_true() {
            when(shares.existsByIdAndExpenseItem_IdAndExpenseItem_Expense_Id(77L, 1000L, 9L)).thenReturn(true);
            assertThat(perms.shareBelongsToItemInExpense(9L, 1000L, 77L)).isTrue();
        }

        @Test
        @DisplayName("canManageExpenseShare: not belong → false")
        void manageShare_notBelong_false() {
            when(shares.existsByIdAndExpenseItem_IdAndExpenseItem_Expense_Id(77L, 1000L, 9L)).thenReturn(false);
            assertThat(perms.canManageExpenseShare(9L, 1000L, 77L)).isFalse();
        }

        @Test
        @DisplayName("canManageExpenseShare: belong + canManageExpense → true")
        void manageShare_belong_and_manageExpense_true() {
            when(shares.existsByIdAndExpenseItem_IdAndExpenseItem_Expense_Id(77L, 1000L, 9L)).thenReturn(true);
            // canManageExpense(9L) → admin true
            when(sec.isAdmin()).thenReturn(true);
            assertThat(perms.canManageExpenseShare(9L, 1000L, 77L)).isTrue();
        }
    }
}
