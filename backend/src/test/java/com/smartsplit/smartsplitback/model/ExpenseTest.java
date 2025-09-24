package com.smartsplit.smartsplitback.model;

import org.junit.jupiter.api.*;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ExpenseTest {

    // ---------- helpers ----------
    private static ExpenseItem item(BigDecimal amount) {
        ExpenseItem it = new ExpenseItem();
        it.setAmount(amount);
        return it;
    }

    private static ExpensePayment payment(BigDecimal amount, PaymentStatus status) {
        ExpensePayment p = new ExpensePayment();
        p.setAmount(amount);
        p.setStatus(status);
        return p;
    }

    // ================= getItemsTotal() =================
    @Nested
    @DisplayName("getItemsTotal()")
    class ItemsTotal {

        @Test
        @DisplayName("รวมเฉพาะ amount ที่ไม่เป็น null")
        void sum_items_ok() {
            Expense e = new Expense();

            e.addItem(item(new BigDecimal("10.50")));
            e.addItem(item(new BigDecimal("0.00")));
            e.addItem(item(new BigDecimal("5.25")));

            // แทรก null amount เพื่อยืนยันว่าเมทอดข้ามได้
            ExpenseItem nullAmt = new ExpenseItem();
            nullAmt.setAmount(null);
            e.addItem(nullAmt);

            assertThat(e.getItemsTotal()).isEqualByComparingTo("15.75"); // 10.50 + 0.00 + 5.25
        }

        @Test
        @DisplayName("ไม่มี item → รวมได้เป็นศูนย์")
        void empty_items_zero() {
            Expense e = new Expense();
            assertThat(e.getItemsTotal()).isEqualByComparingTo("0.00");
        }
    }

    // ================= getVerifiedPaymentsTotal() =================
    @Nested
    @DisplayName("getVerifiedPaymentsTotal()")
    class VerifiedPaymentsTotal {

        @Test
        @DisplayName("รวมเฉพาะ payment ที่ status = VERIFIED และ amount ไม่เป็น null")
        void sum_verified_ok() {
            Expense e = new Expense();

            e.addPayment(payment(new BigDecimal("8.00"),  PaymentStatus.VERIFIED));
            e.addPayment(payment(new BigDecimal("3.00"),  PaymentStatus.PENDING));
            e.addPayment(payment(new BigDecimal("4.50"),  PaymentStatus.VERIFIED));

            // แทรก amount เป็น null → ไม่ควรนับ
            ExpensePayment nullAmt = payment(null, PaymentStatus.VERIFIED);
            e.addPayment(nullAmt);

            assertThat(e.getVerifiedPaymentsTotal()).isEqualByComparingTo("12.50"); // 8.00 + 4.50
        }

        @Test
        @DisplayName("ไม่มี payment → รวมได้เป็นศูนย์")
        void empty_payments_zero() {
            Expense e = new Expense();
            assertThat(e.getVerifiedPaymentsTotal()).isEqualByComparingTo("0.00");
        }
    }

    // ================= addItem/removeItem & addPayment/removePayment =================
    @Nested
    @DisplayName("ความสัมพันธ์แบบสองทาง (bidirectional links)")
    class BidirectionalLinks {

        @Test
        @DisplayName("addItem: ใส่ item เข้า list และตั้ง back-reference ให้ item.expense = this")
        void addItem_setsBackRef() {
            Expense e = new Expense();
            ExpenseItem it = item(new BigDecimal("1.00"));

            e.addItem(it);

            assertThat(e.getItems()).containsExactly(it);
            assertThat(it.getExpense()).isSameAs(e);
        }

        @Test
        @DisplayName("removeItem: เอาออกจาก list และเคลียร์ item.expense = null")
        void removeItem_clearsBackRef() {
            Expense e = new Expense();
            ExpenseItem it = item(new BigDecimal("2.00"));
            e.addItem(it);

            e.removeItem(it);

            assertThat(e.getItems()).isEmpty();
            assertThat(it.getExpense()).isNull();
        }

        @Test
        @DisplayName("addPayment/removePayment: ทำงานเหมือนกันกับ payment")
        void addRemovePayment_ok() {
            Expense e = new Expense();
            ExpensePayment p = payment(new BigDecimal("9.99"), PaymentStatus.VERIFIED);

            e.addPayment(p);
            assertThat(e.getPayments()).containsExactly(p);
            assertThat(p.getExpense()).isSameAs(e);

            e.removePayment(p);
            assertThat(e.getPayments()).isEmpty();
            assertThat(p.getExpense()).isNull();
        }
    }

    // ================= equals/hashCode =================
    @Nested
    @DisplayName("equals/hashCode (ตาม id)")
    class EqualsHashCode {

        @Test
        @DisplayName("id เท่ากัน → equals = true, hashCode เท่ากัน")
        void sameId_equal() {
            Expense a = new Expense();
            a.setId(1L);
            Expense b = new Expense();
            b.setId(1L);

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("id ต่างกัน → equals = false")
        void diffId_notEqual() {
            Expense a = new Expense();
            a.setId(1L);
            Expense b = new Expense();
            b.setId(2L);

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("id เป็น null → ไม่เท่ากับที่มี id")
        void nullId_notEqual() {
            Expense a = new Expense();      // id null
            Expense b = new Expense(); b.setId(1L);

            assertThat(a).isNotEqualTo(b);
            assertThat(b).isNotEqualTo(a);
        }
    }
}
