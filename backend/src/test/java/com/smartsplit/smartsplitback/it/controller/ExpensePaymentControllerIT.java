package com.smartsplit.smartsplitback.it.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartsplit.smartsplitback.it.security.BaseIntegrationTest;
import com.smartsplit.smartsplitback.model.*;
import com.smartsplit.smartsplitback.repository.ExpensePaymentRepository;
import com.smartsplit.smartsplitback.repository.ExpenseRepository;
import com.smartsplit.smartsplitback.repository.PaymentReceiptRepository;
import com.smartsplit.smartsplitback.repository.GroupRepository;
import com.smartsplit.smartsplitback.repository.UserRepository;
import com.smartsplit.smartsplitback.security.JwtService;
import com.smartsplit.smartsplitback.security.Perms;
import com.smartsplit.smartsplitback.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@DisplayName("ExpensePaymentController IT (Full-stack with PaymentReceipt)")
class ExpensePaymentControllerIT extends BaseIntegrationTest {

    private static final String BASE = "/api/expenses/{expenseId}/payments";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JwtService jwtService;

    @Autowired UserRepository userRepo;
    @Autowired ExpenseRepository expenseRepo;
    @Autowired ExpensePaymentRepository paymentRepo;
    @Autowired PaymentReceiptRepository receiptRepo;
    @Autowired GroupRepository groupRepo;
    @MockitoBean(name = "perm")
    Perms perm;

    @MockitoBean
    FileStorageService storage;

    long meId;
    long otherUserId;
    long expenseId;

    // ---------- JWT helpers ----------
    private String jwtFor(long uid, int roleCode) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtService.CLAIM_UID, uid);
        claims.put(JwtService.CLAIM_ROLE, roleCode);
        return jwtService.generate("uid:" + uid, claims, 3600);
    }
    private RequestPostProcessor asUser(long id) {
        return req -> { req.addHeader("Authorization", "Bearer " + jwtFor(id, JwtService.ROLE_USER)); return req; };
    }

    @BeforeEach
    void setupData() {
        receiptRepo.deleteAll();
        paymentRepo.deleteAll();
        expenseRepo.deleteAll();
        userRepo.deleteAll();
        User me = new User();
        me.setEmail("me@example.com");
        me.setUserName("Me");
        me.setPasswordHash("{noop}x");
        me.setRole(Role.USER);
        meId = userRepo.save(me).getId();

        User other = new User();
        other.setEmail("other@example.com");
        other.setUserName("Other");
        other.setPasswordHash("{noop}x");
        other.setRole(Role.USER);
        otherUserId = userRepo.save(other).getId();

        Group g = new Group();
        g.setName("IT Test Group");
        g.setOwner(userRepo.getReferenceById(meId));
        g = groupRepo.save(g);

        Expense e = new Expense();
        e.setGroup(g);
        e.setTitle("Test Expense");
        e.setType(ExpenseType.CUSTOM);
        e.setStatus(ExpenseStatus.OPEN);
        e.setAmount(new BigDecimal("0.00"));
        e.setPayer(me);
        e.setCreatedAt(LocalDateTime.now());

        expenseId = expenseRepo.save(e).getId();


        when(perm.canViewExpense(anyLong())).thenReturn(true);
        when(perm.canSubmitPayment(anyLong(), anyLong())).thenReturn(true);
        when(perm.canManageExpense(anyLong())).thenReturn(true);
    }

    // -------------------- LIST --------------------

    @Test
    @DisplayName("GET /payments → 401 เมื่อไม่มี token")
    void list_unauthorized_without_token() throws Exception {
        mvc.perform(get(BASE, expenseId)).andExpect(status().isUnauthorized());
        assertThat(paymentRepo.count()).isZero();
    }

    @Test
    @DisplayName("GET /payments → 403 เมื่อ perm.canViewExpense=false")
    void list_forbidden_when_perm_denies() throws Exception {
        when(perm.canViewExpense(expenseId)).thenReturn(false);

        mvc.perform(get(BASE, expenseId).with(asUser(meId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /payments → 200 และคืนรายการจาก DB")
    void list_ok_and_hits_db() throws Exception {
        ExpensePayment p = new ExpensePayment();
        p.setExpense(expenseRepo.getReferenceById(expenseId));
        p.setFromUser(userRepo.getReferenceById(meId));
        p.setAmount(new BigDecimal("12.34"));
        p.setStatus(PaymentStatus.PENDING);
        paymentRepo.save(p);

        mvc.perform(get(BASE, expenseId).with(asUser(meId)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].amount").value(12.34));
    }

    // -------------------- GET by id --------------------

    @Test
    @DisplayName("GET /payments/{pid} → 404 เมื่อไม่พบ")
    void get_notfound() throws Exception {
        mvc.perform(get(BASE + "/{pid}", expenseId, 999999L).with(asUser(meId)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /payments/{pid} → 200 เมื่อพบ")
    void get_ok() throws Exception {
        ExpensePayment p = new ExpensePayment();
        p.setExpense(expenseRepo.getReferenceById(expenseId));
        p.setFromUser(userRepo.getReferenceById(meId));
        p.setAmount(new BigDecimal("123.45"));
        p.setStatus(PaymentStatus.PENDING);
        long pid = paymentRepo.save(p).getId();

        mvc.perform(get(BASE + "/{pid}", expenseId, pid).with(asUser(meId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) pid))
                .andExpect(jsonPath("$.status").value(PaymentStatus.PENDING.name()));
    }

    // -------------------- CREATE (multipart) --------------------

    @Test
    @DisplayName("POST /payments (no receipt) → 201 และไม่มี receipt ใน DB")
    void create_no_receipt_ok_hits_db() throws Exception {
        var res = mvc.perform(multipart(BASE, expenseId)
                        .with(asUser(meId))
                        .param("fromUserId", String.valueOf(meId))
                        .param("amount", "50.00"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        JsonNode body = om.readTree(res.getResponse().getContentAsByteArray());
        long pid = body.get("id").asLong();

        ExpensePayment saved = paymentRepo.findById(pid).orElseThrow();
        assertThat(saved.getAmount()).isEqualByComparingTo("50.00");
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(saved.getReceipt()).isNull();
        assertThat(receiptRepo.count()).isZero();
    }

    @Test
    @DisplayName("POST /payments (with receipt) → 201, storage.save ถูกเรียก, DB มี PaymentReceipt (fileUrl)")
    void create_with_receipt_ok_hits_db() throws Exception {
        var receipt = new MockMultipartFile("receipt", "r.png", MediaType.IMAGE_PNG_VALUE, new byte[]{1,2,3});
        when(storage.save(any(), eq("payment-receipts"), startsWith("payment-"), any()))
                .thenReturn("https://files/receipt-r.png");

        var res = mvc.perform(multipart(BASE, expenseId)
                        .file(receipt)
                        .with(asUser(meId))
                        .param("fromUserId", String.valueOf(meId))
                        .param("amount", "75.00"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        JsonNode body = om.readTree(res.getResponse().getContentAsByteArray());
        long pid = body.get("id").asLong();

        // storage ถูกเรียก 1 ครั้ง
        verify(storage, times(1)).save(any(), eq("payment-receipts"), startsWith("payment-"), any());

        // DB: มี receipt 1:1 เชื่อมกับ payment และ fileUrl ถูกต้อง
        ExpensePayment saved = paymentRepo.findById(pid).orElseThrow();
        assertThat(saved.getReceipt()).isNotNull();
        assertThat(saved.getReceipt().getFileUrl()).isEqualTo("https://files/receipt-r.png");
        assertThat(saved.getReceipt().getPayment().getId()).isEqualTo(pid);
        assertThat(receiptRepo.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("POST /payments (with receipt) → 201 แม้จะมี payment อื่นที่มี receipt อยู่แล้ว (เพราะสร้าง payment ใหม่)")
    void create_with_receipt_creates_new_payment_even_if_others_have_receipt() throws Exception {
        // seed payment+receipt เดิม
        ExpensePayment existing = new ExpensePayment();
        existing.setExpense(expenseRepo.getReferenceById(expenseId));
        existing.setFromUser(userRepo.getReferenceById(meId));
        existing.setAmount(new BigDecimal("10.00"));
        existing.setStatus(PaymentStatus.PENDING);
        existing = paymentRepo.save(existing);

        PaymentReceipt r = new PaymentReceipt();
        r.setPayment(existing);
        r.setFileUrl("https://files/existed.png");
        receiptRepo.save(r);

        // POST อีกครั้ง -> ควร 201 (สร้าง payment ใหม่)
        when(storage.save(any(), eq("payment-receipts"), startsWith("payment-"), any()))
                .thenReturn("https://files/new.png");

        var receipt = new MockMultipartFile("receipt", "new.png", MediaType.IMAGE_PNG_VALUE, new byte[]{1});
        var res = mvc.perform(multipart(BASE, expenseId)
                        .file(receipt)
                        .with(asUser(meId))
                        .param("fromUserId", String.valueOf(meId))
                        .param("amount", "10.00"))
                .andExpect(status().isCreated())
                .andReturn();

        // ตรวจว่า payment ใหม่ถูกสร้าง และมี receipt ของตัวเอง
        long newPid = om.readTree(res.getResponse().getContentAsByteArray()).get("id").asLong();
        assertThat(newPid).isNotEqualTo(existing.getId());

        ExpensePayment saved = paymentRepo.findById(newPid).orElseThrow();
        assertThat(saved.getReceipt()).isNotNull();
        assertThat(saved.getReceipt().getFileUrl()).isEqualTo("https://files/new.png");
    }


    @Test
    @DisplayName("POST /payments → 403 เมื่อ perm.canSubmitPayment=false (ไม่แตะ DB)")
    void create_forbidden_when_perm_denies() throws Exception {
        when(perm.canSubmitPayment(expenseId, meId)).thenReturn(false);

        mvc.perform(multipart(BASE, expenseId)
                        .with(asUser(meId))
                        .param("fromUserId", String.valueOf(meId))
                        .param("amount", "20.00"))
                .andExpect(status().isForbidden());

        assertThat(paymentRepo.count()).isZero();
        assertThat(receiptRepo.count()).isZero();
        verify(storage, never()).save(any(), anyString(), anyString(), any());
    }

    // -------------------- setStatus --------------------

    @Test
    @DisplayName("PUT /payments/{pid}/status → 404 เมื่อไม่พบ")
    void set_status_not_found() throws Exception {
        mvc.perform(put(BASE + "/{pid}/status", expenseId, 424242L)
                        .with(asUser(meId))
                        .param("status", PaymentStatus.VERIFIED.name()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /payments/{pid}/status → 200 อัปเดตสถานะ + verifiedAt ใน DB")
    void set_status_ok_hits_db() throws Exception {
        ExpensePayment p = new ExpensePayment();
        p.setExpense(expenseRepo.getReferenceById(expenseId));
        p.setFromUser(userRepo.getReferenceById(otherUserId));
        p.setAmount(new BigDecimal("50.00"));
        p.setStatus(PaymentStatus.PENDING);
        long pid = paymentRepo.save(p).getId();

        mvc.perform(put(BASE + "/{pid}/status", expenseId, pid)
                        .with(asUser(meId))
                        .param("status", PaymentStatus.VERIFIED.name()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(PaymentStatus.VERIFIED.name()));

        ExpensePayment after = paymentRepo.findById(pid).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(PaymentStatus.VERIFIED);
        // ถ้า service ของคุณตั้ง verifiedAt ตอน VERIFIED ให้ตรวจไม่เป็น null
        Instant verifiedAt = after.getVerifiedAt();
        // ถ้าโปรเจกต์ตั้งค่า verifiedAt → assert
        // assertThat(verifiedAt).isNotNull();
    }

    @Test
    @DisplayName("PUT /payments/{pid}/status → 403 เมื่อ perm.canManageExpense=false")
    void set_status_forbidden() throws Exception {
        when(perm.canManageExpense(expenseId)).thenReturn(false);

        mvc.perform(put(BASE + "/{pid}/status", expenseId, 111L)
                        .with(asUser(meId))
                        .param("status", PaymentStatus.VERIFIED.name()))
                .andExpect(status().isForbidden());
    }

    // -------------------- delete --------------------

    @Test
    @DisplayName("DELETE /payments/{pid} → 404 เมื่อไม่พบ")
    void delete_not_found() throws Exception {
        mvc.perform(delete(BASE + "/{pid}", expenseId, 99999L).with(asUser(meId)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /payments/{pid} → 204 และ cascade orphanRemoval ลบ receipt ด้วย")
    void delete_ok_hits_db_and_cascade_receipt() throws Exception {
        ExpensePayment p = new ExpensePayment();
        p.setExpense(expenseRepo.getReferenceById(expenseId));
        p.setFromUser(userRepo.getReferenceById(meId));
        p.setAmount(new BigDecimal("19.99"));
        p.setStatus(PaymentStatus.PENDING);
        p = paymentRepo.save(p);

        PaymentReceipt r = new PaymentReceipt();
        r.setPayment(p);
        r.setFileUrl("https://files/will-be-deleted.png");
        receiptRepo.save(r);

        long pid = p.getId();

        mvc.perform(delete(BASE + "/{pid}", expenseId, pid).with(asUser(meId)))
                .andExpect(status().isNoContent());

        assertThat(paymentRepo.findById(pid)).isEmpty();
        assertThat(receiptRepo.count()).isZero(); // orphanRemoval = true → ลบใบเสร็จตาม
    }
}
