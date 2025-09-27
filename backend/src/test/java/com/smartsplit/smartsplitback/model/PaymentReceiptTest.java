package com.smartsplit.smartsplitback.model;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentReceiptTest {

    private static ExpensePayment payment(Long id, String amountStr) {
        ExpensePayment p = new ExpensePayment();
        p.setId(id);
       
        return p;
    }

    @Nested
    @DisplayName("setter/getter พื้นฐาน + alias")
    class Basics {
        @Test
        @DisplayName("setPayment / setExpensePayment ควรชี้ไป instance เดียวกัน")
        void setPayment_alias() {
            PaymentReceipt r = new PaymentReceipt();

            ExpensePayment p1 = payment(1L, "10.00");
            ExpensePayment p2 = payment(2L, "20.00");

            r.setPayment(p1);
            assertThat(r.getPayment()).isSameAs(p1);

            // alias ที่คลาสมีไว้: setExpensePayment
            r.setExpensePayment(p2);
            assertThat(r.getPayment()).isSameAs(p2);

            r.setFileUrl("http://x/file.jpg");
            assertThat(r.getFileUrl()).isEqualTo("http://x/file.jpg");
        }
    }

    @Nested
    @DisplayName("equals/hashCode (อิง id)")
    class Equality {
        @Test
        @DisplayName("id เท่ากัน → equals=true, hashCode เท่ากัน")
        void sameId_equal() {
            PaymentReceipt a = new PaymentReceipt();
            a.setId(10L);
            PaymentReceipt b = new PaymentReceipt();
            b.setId(10L);

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("id ต่างกัน → not equal")
        void diffId_notEqual() {
            PaymentReceipt a = new PaymentReceipt();
            a.setId(1L);
            PaymentReceipt b = new PaymentReceipt();
            b.setId(2L);

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("id null vs มี id → not equal")
        void nullId_notEqual() {
            PaymentReceipt a = new PaymentReceipt();     // id = null
            PaymentReceipt b = new PaymentReceipt();
            b.setId(1L);

            assertThat(a).isNotEqualTo(b);
            assertThat(b).isNotEqualTo(a);
        }
    }
}
