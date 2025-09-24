package com.smartsplit.smartsplitback.service;

import com.smartsplit.smartsplitback.model.PaymentReceipt;
import com.smartsplit.smartsplitback.repository.PaymentReceiptRepository;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentReceiptServiceTest {

    @Mock private PaymentReceiptRepository receipts;
    @InjectMocks private PaymentReceiptService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }


    private static PaymentReceipt receipt(Long id) {
        PaymentReceipt r = new PaymentReceipt();
        r.setId(id);
        return r;
    }


    @Nested
    @DisplayName("get(id)")
    class GetById {

        @Test
        @DisplayName("พบ → คืน PaymentReceipt")
        void found() {
            when(receipts.findById(10L)).thenReturn(Optional.of(receipt(10L)));

            PaymentReceipt r = service.get(10L);

            assertThat(r).isNotNull();
            assertThat(r.getId()).isEqualTo(10L);
            verify(receipts).findById(10L);
            verifyNoMoreInteractions(receipts);
        }

        @Test
        @DisplayName("ไม่พบ → คืน null")
        void notFound() {
            when(receipts.findById(99L)).thenReturn(Optional.empty());

            PaymentReceipt r = service.get(99L);

            assertThat(r).isNull();
            verify(receipts).findById(99L);
            verifyNoMoreInteractions(receipts);
        }
    }


    @Nested
    @DisplayName("getByPaymentId(paymentId)")
    class GetByPaymentId {

        @Test
        @DisplayName("พบ → คืน PaymentReceipt")
        void found() {
            when(receipts.findByPayment_Id(77L)).thenReturn(Optional.of(receipt(5L)));

            PaymentReceipt r = service.getByPaymentId(77L);

            assertThat(r).isNotNull();
            assertThat(r.getId()).isEqualTo(5L);
            verify(receipts).findByPayment_Id(77L);
            verifyNoMoreInteractions(receipts);
        }

        @Test
        @DisplayName("ไม่พบ → คืน null")
        void notFound() {
            when(receipts.findByPayment_Id(88L)).thenReturn(Optional.empty());

            PaymentReceipt r = service.getByPaymentId(88L);

            assertThat(r).isNull();
            verify(receipts).findByPayment_Id(88L);
            verifyNoMoreInteractions(receipts);
        }
    }


    @Nested
    @DisplayName("delete(id)")
    class DeleteById {

        @Test
        @DisplayName("มีอยู่จริง → เรียก deleteById สำเร็จ")
        void delete_ok() {
            when(receipts.existsById(12L)).thenReturn(true);

            service.delete(12L);

            verify(receipts).existsById(12L);
            verify(receipts).deleteById(12L);
            verifyNoMoreInteractions(receipts);
        }

        @Test
        @DisplayName("ไม่มี → โยน 404 'Receipt not found' และไม่ลบ")
        void delete_notFound_404() {
            when(receipts.existsById(13L)).thenReturn(false);

            ResponseStatusException ex = catchThrowableOfType(
                    () -> service.delete(13L),
                    ResponseStatusException.class
            );

            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value());
            assertThat(ex.getReason()).isEqualTo("Receipt not found");

            verify(receipts).existsById(13L);
            verify(receipts, never()).deleteById(anyLong());
            verifyNoMoreInteractions(receipts);
        }
    }
}
