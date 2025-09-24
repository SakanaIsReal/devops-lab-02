package com.smartsplit.smartsplitback.service;

import com.smartsplit.smartsplitback.model.Expense;
import com.smartsplit.smartsplitback.model.ExpensePayment;
import com.smartsplit.smartsplitback.model.PaymentReceipt;
import com.smartsplit.smartsplitback.model.PaymentStatus;
import com.smartsplit.smartsplitback.model.User;
import com.smartsplit.smartsplitback.repository.ExpensePaymentRepository;
import com.smartsplit.smartsplitback.repository.ExpenseRepository;
import com.smartsplit.smartsplitback.repository.PaymentReceiptRepository;
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

class ExpensePaymentServiceTest {

    @Mock private ExpensePaymentRepository payments;
    @Mock private ExpenseRepository expenses;
    @Mock private UserRepository users;
    @Mock private PaymentReceiptRepository receipts;
    @Mock private FileStorageService storage;

    @InjectMocks private ExpensePaymentService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // ===== Helpers =====
    private static Expense exp(Long id) {
        Expense e = new Expense();
        e.setId(id);
        return e;
    }

    private static User user(Long id, String email) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        return u;
    }

    private static ExpensePayment payment(Long id, Expense e, User from, BigDecimal amount, PaymentStatus status) {
        ExpensePayment p = new ExpensePayment();
        p.setId(id);
        p.setExpense(e);
        p.setFromUser(from);
        p.setAmount(amount);
        p.setStatus(status);
        return p;
    }

    private static PaymentReceipt receipt(Long id, ExpensePayment p, String url) {
        PaymentReceipt r = new PaymentReceipt();
        r.setId(id);
        r.setExpensePayment(p);
        r.setFileUrl(url);
        return r;
    }

    // ============================== listByExpense ==============================
    @Test
    @DisplayName("listByExpense: คืน payments ทุกตัวของ expense")
    void listByExpense_ok() {
        Long expenseId = 10L;
        when(payments.findByExpense_Id(expenseId)).thenReturn(List.of(
                payment(1L, exp(expenseId), user(2L, "a@x"), new BigDecimal("10.00"), PaymentStatus.PENDING)
        ));

        List<ExpensePayment> result = service.listByExpense(expenseId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        verify(payments).findByExpense_Id(expenseId);
    }

    // ============================== get / getInExpense ==============================
    @Nested
    @DisplayName("get(paymentId) / getInExpense(expenseId, paymentId)")
    class Getters {

        @Test
        @DisplayName("get: พบ → คืน payment, ไม่พบ → null")
        void get_ok_null() {
            when(payments.findById(1L)).thenReturn(Optional.of(payment(1L, exp(1L), user(1L, "u@x"),
                    new BigDecimal("5.00"), PaymentStatus.PENDING)));
            when(payments.findById(99L)).thenReturn(Optional.empty());

            assertThat(service.get(1L)).isNotNull();
            assertThat(service.get(99L)).isNull();

            verify(payments).findById(1L);
            verify(payments).findById(99L);
        }

        @Test
        @DisplayName("getInExpense: พบใน expense นั้น → คืน, ไม่พบ → null")
        void getInExpense_ok_null() {
            Long expenseId = 10L, payId = 100L;
            when(payments.findByIdAndExpense_Id(payId, expenseId)).thenReturn(
                    Optional.of(payment(payId, exp(expenseId), user(2L, "a@x"),
                            new BigDecimal("8.00"), PaymentStatus.PENDING))
            );
            when(payments.findByIdAndExpense_Id(999L, expenseId)).thenReturn(Optional.empty());

            assertThat(service.getInExpense(expenseId, payId)).isNotNull();
            assertThat(service.getInExpense(expenseId, 999L)).isNull();

            verify(payments).findByIdAndExpense_Id(payId, expenseId);
            verify(payments).findByIdAndExpense_Id(999L, expenseId);
        }
    }

    // ============================== create ==============================
    @Nested
    @DisplayName("create(expenseId, fromUserId, amount)")
    class CreatePayment {

        @Test
        @DisplayName("happy path: amount > 0 → scale เป็น 2 ตำแหน่ง, status=PENDING, save")
        void create_ok() {
            Long expenseId = 20L, fromUserId = 3L;
            when(expenses.findById(expenseId)).thenReturn(Optional.of(exp(expenseId)));
            when(users.findById(fromUserId)).thenReturn(Optional.of(user(fromUserId, "p@x")));
            when(payments.save(any(ExpensePayment.class))).thenAnswer(inv -> {
                ExpensePayment p = inv.getArgument(0);
                p.setId(777L);
                return p;
            });

            ExpensePayment created = service.create(expenseId, fromUserId, new BigDecimal("1.999"));

            assertThat(created.getId()).isEqualTo(777L);
            assertThat(created.getExpense().getId()).isEqualTo(expenseId);
            assertThat(created.getFromUser().getId()).isEqualTo(fromUserId);
            assertThat(created.getAmount()).isEqualByComparingTo("2.00"); // scaleMoney(1.999)
            assertThat(created.getStatus()).isEqualTo(PaymentStatus.PENDING);

            verify(expenses).findById(expenseId);
            verify(users).findById(fromUserId);
            verify(payments).save(any(ExpensePayment.class));
        }

        @Test
        @DisplayName("amount == null → 400 BAD_REQUEST")
        void create_amountNull_400() {
            ResponseStatusException ex = catchThrowableOfType(
                    () -> service.create(1L, 1L, null),
                    ResponseStatusException.class
            );
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value());
            assertThat(ex.getReason()).isEqualTo("Amount must be > 0");

            verifyNoInteractions(expenses, users, payments);
        }

        @Test
        @DisplayName("amount <= 0 → 400 BAD_REQUEST")
        void create_amountNonPositive_400() {
            ResponseStatusException ex = catchThrowableOfType(
                    () -> service.create(1L, 1L, new BigDecimal("0.00")),
                    ResponseStatusException.class
            );
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value());
            assertThat(ex.getReason()).isEqualTo("Amount must be > 0");

            verifyNoInteractions(expenses, users, payments);
        }

        @Test
        @DisplayName("expense ไม่พบ → 404 NOT_FOUND")
        void create_expenseNotFound_404() {
            when(expenses.findById(99L)).thenReturn(Optional.empty());

            ResponseStatusException ex = catchThrowableOfType(
                    () -> service.create(99L, 1L, BigDecimal.ONE),
                    ResponseStatusException.class
            );
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value());
            assertThat(ex.getReason()).isEqualTo("Expense not found");

            verify(expenses).findById(99L);
            verifyNoInteractions(users, payments);
        }

        @Test
        @DisplayName("user ไม่พบ → 404 NOT_FOUND")
        void create_userNotFound_404() {
            when(expenses.findById(1L)).thenReturn(Optional.of(exp(1L)));
            when(users.findById(999L)).thenReturn(Optional.empty());

            ResponseStatusException ex = catchThrowableOfType(
                    () -> service.create(1L, 999L, BigDecimal.ONE),
                    ResponseStatusException.class
            );
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value());
            assertThat(ex.getReason()).isEqualTo("User (payer) not found");

            verify(users).findById(999L);
            verifyNoInteractions(payments);
        }
    }

    // ============================== setStatus / setStatusInExpense ==============================
    @Nested
    @DisplayName("setStatus(paymentId, status) / setStatusInExpense(expenseId, paymentId, status)")
    class SetStatus {

        @Test
        @DisplayName("setStatus: พบ → อัปเดต status แล้ว save")
        void setStatus_ok() {
            ExpensePayment p = payment(1L, exp(1L), user(2L, "a@x"), new BigDecimal("3.00"), PaymentStatus.PENDING);
            when(payments.findById(1L)).thenReturn(Optional.of(p));
            when(payments.save(any(ExpensePayment.class))).thenAnswer(inv -> inv.getArgument(0));

            ExpensePayment updated = service.setStatus(1L, PaymentStatus.VERIFIED);

            assertThat(updated.getStatus()).isEqualTo(PaymentStatus.VERIFIED);
            verify(payments).findById(1L);
            verify(payments).save(p);
        }

        @Test
        @DisplayName("setStatus: ไม่พบ → 404")
        void setStatus_notFound_404() {
            when(payments.findById(999L)).thenReturn(Optional.empty());

            ResponseStatusException ex = catchThrowableOfType(
                    () -> service.setStatus(999L, PaymentStatus.VERIFIED),
                    ResponseStatusException.class
            );
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value());
            assertThat(ex.getReason()).isEqualTo("Payment not found");
        }

        @Test
        @DisplayName("setStatusInExpense: พบใน expense นั้น → อัปเดตและ save")
        void setStatusInExpense_ok() {
            Long expenseId = 10L, paymentId = 100L;
            ExpensePayment p = payment(paymentId, exp(expenseId), user(1L, "x@x"), new BigDecimal("1.00"), PaymentStatus.PENDING);
            when(payments.findByIdAndExpense_Id(paymentId, expenseId)).thenReturn(Optional.of(p));
            when(payments.save(any(ExpensePayment.class))).thenAnswer(inv -> inv.getArgument(0));

            ExpensePayment updated = service.setStatusInExpense(expenseId, paymentId, PaymentStatus.REJECTED);

            assertThat(updated.getStatus()).isEqualTo(PaymentStatus.REJECTED);
            verify(payments).findByIdAndExpense_Id(paymentId, expenseId);
            verify(payments).save(p);
        }

        @Test
        @DisplayName("setStatusInExpense: ไม่พบ → 404")
        void setStatusInExpense_notFound_404() {
            when(payments.findByIdAndExpense_Id(9L, 99L)).thenReturn(Optional.empty());

            ResponseStatusException ex = catchThrowableOfType(
                    () -> service.setStatusInExpense(99L, 9L, PaymentStatus.REJECTED),
                    ResponseStatusException.class
            );
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value());
            assertThat(ex.getReason()).isEqualTo("Payment not found in this expense");
        }
    }

    // ============================== delete ==============================
    @Nested
    @DisplayName("delete(paymentId)")
    class DeletePayment {

        @Test
        @DisplayName("มีใบเสร็จ → ลบไฟล์ + ลบใบเสร็จ จากนั้นลบ payment")
        void delete_withReceipt_thenDeletePayment() {
            Long paymentId = 5L;
            ExpensePayment p = payment(paymentId, exp(1L), user(1L, "x@x"), new BigDecimal("1.00"), PaymentStatus.PENDING);
            PaymentReceipt r = receipt(50L, p, "http://file.url/rec.jpg");

            when(receipts.findByPayment_Id(paymentId)).thenReturn(Optional.of(r));
            when(payments.existsById(paymentId)).thenReturn(true);

            service.delete(paymentId);

            verify(storage).deleteByUrl("http://file.url/rec.jpg");
            verify(receipts).delete(r);
            verify(payments).existsById(paymentId);
            verify(payments).deleteById(paymentId);
        }

        @Test
        @DisplayName("ไม่มีใบเสร็จ แต่ payment มี → ลบ payment ได้")
        void delete_noReceipt_butPaymentExists() {
            Long paymentId = 6L;
            when(receipts.findByPayment_Id(paymentId)).thenReturn(Optional.empty());
            when(payments.existsById(paymentId)).thenReturn(true);

            service.delete(paymentId);

            verify(storage, never()).deleteByUrl(anyString());
            verify(receipts, never()).delete(any());
            verify(payments).deleteById(paymentId);
        }

        @Test
        @DisplayName("มีใบเสร็จ แต่ payment ไม่มีอยู่ → ลบใบเสร็จแล้ว โยน 404")
        void delete_receiptExists_butPaymentMissing_404() {
            Long paymentId = 7L;
            PaymentReceipt r = receipt(70L, payment(paymentId, exp(1L), user(1L, "x@x"), BigDecimal.ONE, PaymentStatus.PENDING),
                    "s3://bucket/key");
            when(receipts.findByPayment_Id(paymentId)).thenReturn(Optional.of(r));
            when(payments.existsById(paymentId)).thenReturn(false);

            ResponseStatusException ex = catchThrowableOfType(
                    () -> service.delete(paymentId),
                    ResponseStatusException.class
            );

            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value());
            assertThat(ex.getReason()).isEqualTo("Payment not found");

            verify(storage).deleteByUrl("s3://bucket/key");
            verify(receipts).delete(r);
            verify(payments, never()).deleteById(anyLong());
        }
    }

    // ============================== deleteInExpense ==============================
    @Nested
    @DisplayName("deleteInExpense(expenseId, paymentId)")
    class DeleteInExpense {

        @Test
        @DisplayName("พบใน expense: มีใบเสร็จ → ลบไฟล์ + ใบเสร็จ แล้วลบ payment")
        void deleteInExpense_withReceipt() {
            Long expenseId = 10L, paymentId = 100L;
            ExpensePayment p = payment(paymentId, exp(expenseId), user(1L, "x@x"), new BigDecimal("1.00"), PaymentStatus.PENDING);
            PaymentReceipt r = receipt(1L, p, "url://receipt");

            when(payments.findByIdAndExpense_Id(paymentId, expenseId)).thenReturn(Optional.of(p));
            when(receipts.findByPayment_Id(paymentId)).thenReturn(Optional.of(r));

            service.deleteInExpense(expenseId, paymentId);

            verify(storage).deleteByUrl("url://receipt");
            verify(receipts).delete(r);
            verify(payments).deleteById(paymentId);
        }

        @Test
        @DisplayName("พบใน expense: ไม่มีใบเสร็จ → ลบ payment ตรง ๆ")
        void deleteInExpense_noReceipt() {
            Long expenseId = 10L, paymentId = 101L;
            ExpensePayment p = payment(paymentId, exp(expenseId), user(1L, "x@x"), BigDecimal.ONE, PaymentStatus.PENDING);

            when(payments.findByIdAndExpense_Id(paymentId, expenseId)).thenReturn(Optional.of(p));
            when(receipts.findByPayment_Id(paymentId)).thenReturn(Optional.empty());

            service.deleteInExpense(expenseId, paymentId);

            verify(storage, never()).deleteByUrl(anyString());
            verify(receipts, never()).delete(any());
            verify(payments).deleteById(paymentId);
        }

        @Test
        @DisplayName("ไม่พบใน expense → 404")
        void deleteInExpense_notFound_404() {
            when(payments.findByIdAndExpense_Id(9L, 99L)).thenReturn(Optional.empty());

            ResponseStatusException ex = catchThrowableOfType(
                    () -> service.deleteInExpense(99L, 9L),
                    ResponseStatusException.class
            );
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value());
            assertThat(ex.getReason()).isEqualTo("Payment not found in this expense");

            verify(payments).findByIdAndExpense_Id(9L, 99L);
            verifyNoInteractions(receipts, storage);
        }
    }

    // ============================== attachReceipt / attachReceiptInExpense ==============================
    @Nested
    @DisplayName("attachReceipt(paymentId, fileUrl) / attachReceiptInExpense(expenseId, paymentId, fileUrl)")
    class AttachReceipt {

        @Test
        @DisplayName("attachReceipt: fileUrl ว่าง/null → 400 BAD_REQUEST")
        void attachReceipt_badUrl_400() {
            ResponseStatusException ex1 = catchThrowableOfType(
                    () -> service.attachReceipt(1L, null),
                    ResponseStatusException.class
            );
            ResponseStatusException ex2 = catchThrowableOfType(
                    () -> service.attachReceipt(1L, "  "),
                    ResponseStatusException.class
            );
            assertThat(ex1.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value());
            assertThat(ex1.getReason()).isEqualTo("fileUrl is required");
            assertThat(ex2.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value());
            assertThat(ex2.getReason()).isEqualTo("fileUrl is required");

            verifyNoInteractions(payments, receipts);
        }

        @Test
        @DisplayName("attachReceipt: payment ไม่พบ → 404")
        void attachReceipt_paymentNotFound_404() {
            when(payments.findById(99L)).thenReturn(Optional.empty());

            ResponseStatusException ex = catchThrowableOfType(
                    () -> service.attachReceipt(99L, "url"),
                    ResponseStatusException.class
            );
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value());
            assertThat(ex.getReason()).isEqualTo("Payment not found");
        }

        @Test
        @DisplayName("attachReceipt: มีใบเสร็จอยู่แล้ว → 409 CONFLICT")
        void attachReceipt_conflict_409() {
            when(payments.findById(1L)).thenReturn(Optional.of(payment(1L, exp(1L), user(1L, "x@x"),
                    BigDecimal.ONE, PaymentStatus.PENDING)));
            when(receipts.findByPayment_Id(1L)).thenReturn(Optional.of(receipt(10L, null, "x")));

            ResponseStatusException ex = catchThrowableOfType(
                    () -> service.attachReceipt(1L, "url"),
                    ResponseStatusException.class
            );
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.CONFLICT.value());
            assertThat(ex.getReason()).isEqualTo("Receipt already exists for this payment");
        }

        @Test
        @DisplayName("attachReceipt: สำเร็จ → save ใบเสร็จ (trim url)")
        void attachReceipt_ok() {
            ExpensePayment p = payment(1L, exp(1L), user(1L, "x@x"), BigDecimal.TEN, PaymentStatus.PENDING);
            when(payments.findById(1L)).thenReturn(Optional.of(p));
            when(receipts.findByPayment_Id(1L)).thenReturn(Optional.empty());
            when(receipts.save(any(PaymentReceipt.class))).thenAnswer(inv -> {
                PaymentReceipt r = inv.getArgument(0);
                r.setId(77L);
                return r;
            });

            PaymentReceipt r = service.attachReceipt(1L, "  http://u  ");

            assertThat(r.getId()).isEqualTo(77L);
            assertThat(r.getPayment()).isSameAs(p);
            assertThat(r.getFileUrl()).isEqualTo("http://u");
            verify(receipts).save(any(PaymentReceipt.class));
        }

        @Test
        @DisplayName("attachReceiptInExpense: ไม่พบ payment ใน expense → 404")
        void attachReceiptInExpense_notInExpense_404() {
            when(payments.existsByIdAndExpense_Id(9L, 99L)).thenReturn(false);

            ResponseStatusException ex = catchThrowableOfType(
                    () -> service.attachReceiptInExpense(99L, 9L, "url"),
                    ResponseStatusException.class
            );
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value());
            assertThat(ex.getReason()).isEqualTo("Payment not found in this expense");
        }

        @Test
        @DisplayName("attachReceiptInExpense: มีใบเสร็จอยู่แล้ว → 409")
        void attachReceiptInExpense_conflict_409() {
            when(payments.existsByIdAndExpense_Id(1L, 10L)).thenReturn(true);
            when(receipts.findByPayment_Id(1L)).thenReturn(Optional.of(receipt(1L, null, "x")));

            ResponseStatusException ex = catchThrowableOfType(
                    () -> service.attachReceiptInExpense(10L, 1L, "url"),
                    ResponseStatusException.class
            );
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.CONFLICT.value());
            assertThat(ex.getReason()).isEqualTo("Receipt already exists for this payment");
        }

        @Test
        @DisplayName("attachReceiptInExpense: สำเร็จ (delegates ไปที่ attachReceipt)")
        void attachReceiptInExpense_ok() {
            Long expenseId = 10L, paymentId = 100L;
            when(payments.existsByIdAndExpense_Id(paymentId, expenseId)).thenReturn(true);
            when(receipts.findByPayment_Id(paymentId)).thenReturn(Optional.empty());

            // ให้ attachReceipt ภายในทำงานครบ
            ExpensePayment p = payment(paymentId, exp(expenseId), user(1L, "x@x"), BigDecimal.ONE, PaymentStatus.PENDING);
            when(payments.findById(paymentId)).thenReturn(Optional.of(p));
            when(receipts.save(any(PaymentReceipt.class))).thenAnswer(inv -> inv.getArgument(0));

            PaymentReceipt r = service.attachReceiptInExpense(expenseId, paymentId, "url://file");

            assertThat(r.getPayment()).isSameAs(p);
            assertThat(r.getFileUrl()).isEqualTo("url://file");
            verify(payments).existsByIdAndExpense_Id(paymentId, expenseId);
            verify(receipts, times(2)).findByPayment_Id(paymentId);
            verify(receipts).save(any(PaymentReceipt.class));
        }
    }

    // ============================== sumVerified ==============================
    @Test
    @DisplayName("sumVerified(expenseId): รวมยอด VERIFIED จาก repo")
    void sumVerified_ok() {
        Long expenseId = 88L;
        when(payments.sumVerifiedAmountByExpenseId(expenseId)).thenReturn(new BigDecimal("123.45"));

        BigDecimal sum = service.sumVerified(expenseId);

        assertThat(sum).isEqualByComparingTo("123.45");
        verify(payments).sumVerifiedAmountByExpenseId(expenseId);
    }
}
