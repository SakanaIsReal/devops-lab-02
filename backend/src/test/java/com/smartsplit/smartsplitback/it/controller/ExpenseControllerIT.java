package com.smartsplit.smartsplitback.it.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartsplit.smartsplitback.it.security.BaseIntegrationTest;
import com.smartsplit.smartsplitback.model.*;
import com.smartsplit.smartsplitback.repository.*;
import com.smartsplit.smartsplitback.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("ExpenseController IT (end-to-end to DB)")
class ExpenseControllerIT extends BaseIntegrationTest {

    private static final String BASE = "/api/expenses";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JwtService jwtService;

    @Autowired GroupRepository groupRepo;
    @Autowired ExpenseRepository expenseRepo;
    @Autowired ExpenseItemRepository itemRepo;
    @Autowired GroupMemberRepository memberRepo;
    @Autowired UserRepository userRepo;

    @Autowired(required = false) ExpensePaymentRepository paymentRepo;

    long adminId;
    long memberId;
    long outsiderId;

    long groupId;
    long expenseId;
    long itemId1;
    long itemId2;

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
    private RequestPostProcessor asAdmin(long id) {
        return req -> { req.addHeader("Authorization", "Bearer " + jwtFor(id, JwtService.ROLE_ADMIN)); return req; };
    }

    @BeforeEach
    void setUp() {

        if (paymentRepo != null) paymentRepo.deleteAll();
        itemRepo.deleteAll();
        expenseRepo.deleteAll();
        memberRepo.deleteAll();
        groupRepo.deleteAll();
        userRepo.deleteAll();

        // ----- users -----
        var admin = new User();
        admin.setEmail("admin@example.com");
        admin.setUserName("Admin");
        admin.setPasswordHash("{noop}x");
        admin.setRole(Role.ADMIN);
        adminId = userRepo.save(admin).getId();

        var mem = new User();
        mem.setEmail("mem@example.com");
        mem.setUserName("Member");
        mem.setPasswordHash("{noop}x");
        mem.setRole(Role.USER);
        memberId = userRepo.save(mem).getId();

        var out = new User();
        out.setEmail("out@example.com");
        out.setUserName("Outsider");
        out.setPasswordHash("{noop}x");
        out.setRole(Role.USER);
        outsiderId = userRepo.save(out).getId();

        // ----- group -----
        var g = new Group();
        g.setName("E2E-Group");
        g.setOwner(userRepo.getReferenceById(memberId));
        groupId = groupRepo.save(g).getId();

        // member เข้ากลุ่ม
        var gm = new GroupMember();
        gm.setGroup(g);
        gm.setUser(mem);
        memberRepo.save(gm);

        // ----- expense หลัก (payer = member) -----
        var e = new Expense();
        e.setGroup(g);
        e.setPayer(mem);
        e.setAmount(new BigDecimal("0.00"));
        e.setType(ExpenseType.CUSTOM);
        e.setTitle("Team Lunch");
        e.setStatus(ExpenseStatus.OPEN);

        expenseId = expenseRepo.save(e).getId();

        // ----- items ใต้ expense -----
        var it1 = new ExpenseItem();
        it1.setExpense(e);
        it1.setName("Noodles");
        it1.setAmount(new BigDecimal("150.00"));
        itemId1 = itemRepo.save(it1).getId();

        var it2 = new ExpenseItem();
        it2.setExpense(e);
        it2.setName("Drinks");
        it2.setAmount(new BigDecimal("50.00"));
        itemId2 = itemRepo.save(it2).getId();

        // ----- payments (ไว้ใช้ /total-verified และ /summary) -----
        if (paymentRepo != null) {
            User payer = userRepo.findById(memberId).orElseThrow();
            User someone = payer;
            var p1 = new ExpensePayment();
            p1.setExpense(e);
            p1.setAmount(new BigDecimal("30.00"));
            p1.setStatus(PaymentStatus.VERIFIED);
            p1.setFromUser(someone);

            paymentRepo.save(p1);

            var p2 = new ExpensePayment();
            p2.setExpense(e);
            p2.setAmount(new BigDecimal("20.00"));
            p2.setStatus(PaymentStatus.PENDING);
            p2.setFromUser(someone);
            paymentRepo.save(p2);
        }
    }

    // -------------------- LIST (ADMIN only) --------------------

    @Test
    @DisplayName("GET /api/expenses → 200 สำหรับ ADMIN, 403 สำหรับ USER")
    void list_admin_only() throws Exception {
        mvc.perform(get(BASE).with(asAdmin(adminId)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        mvc.perform(get(BASE).with(asUser(memberId)))
                .andExpect(status().isForbidden());
    }

    // -------------------- GET by id --------------------

    @Test
    @DisplayName("GET /api/expenses/{id} → 200 สำหรับสมาชิกกลุ่ม")
    void get_ok_for_member() throws Exception {
        mvc.perform(get(BASE + "/{id}", expenseId).with(asUser(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) expenseId))
                .andExpect(jsonPath("$.title").value("Team Lunch"))
                .andExpect(jsonPath("$.groupId").value((int) groupId))
                .andExpect(jsonPath("$.payerUserId").value((int) memberId));
    }

    @Test
    @DisplayName("GET /api/expenses/{id} → 403 เมื่อไม่ใช่สมาชิกกลุ่ม")
    void get_forbidden_for_outsider() throws Exception {
        mvc.perform(get(BASE + "/{id}", expenseId).with(asUser(outsiderId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/expenses/{id} → 404 เมื่อไม่พบ")
    void get_not_found() throws Exception {
        mvc.perform(get(BASE + "/{id}", 999999).with(asAdmin(adminId)))
                .andExpect(status().isNotFound());
    }

    // -------------------- list by group (member) --------------------

    @Test
    @DisplayName("GET /api/expenses/group/{groupId} → 200 สำหรับสมาชิก, 403 สำหรับคนนอก")
    void listByGroup_member_only() throws Exception {
        mvc.perform(get(BASE + "/group/{gid}", groupId).with(asUser(memberId)))
                .andExpect(status().isOk());

        mvc.perform(get(BASE + "/group/{gid}", groupId).with(asUser(outsiderId)))
                .andExpect(status().isForbidden());
    }

    // -------------------- CREATE --------------------

    @Test
    @DisplayName("POST /api/expenses → 201 เมื่อสมาชิกสร้างในกลุ่มตัวเอง (canCreateExpenseInGroup)")
    void create_created_as_member() throws Exception {
        String body = """
                {
                  "groupId": %d,
                  "payerUserId": %d,
                  "amount": 100.00,
                  "type": "CUSTOM",
                  "title": "New Expense",
                  "status": "OPEN"
                }
                """.formatted(groupId, memberId);

        mvc.perform(post(BASE)
                        .with(asUser(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.title").value("New Expense"));

        // ตรวจจาก DB
        var all = expenseRepo.findByGroup_Id(groupId);
        assertThat(all.stream().anyMatch(x -> "New Expense".equals(x.getTitle()))).isTrue();
    }

    @Test
    @DisplayName("POST /api/expenses → 400 เมื่อขาด groupId/payerUserId")
    void create_bad_request_missing_fields() throws Exception {
        String body = """
                {"title":"Bad","status":"OPEN","type":"NORMAL","amount":10.0}
                """;
        mvc.perform(post(BASE)
                        .with(asUser(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest());
    }

    // -------------------- UPDATE --------------------

    @Test
    @DisplayName("PUT /api/expenses/{id} → 200 เมื่อ ADMIN อัปเดตสำเร็จ")
    void update_ok_as_admin() throws Exception {
        String body = """
                {"title":"Edited Title"}
                """;
        mvc.perform(put(BASE + "/{id}", expenseId)
                        .with(asAdmin(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Edited Title"));

        var updated = expenseRepo.findById(expenseId).orElseThrow();
        assertThat(updated.getTitle()).isEqualTo("Edited Title");
    }

    @Test
    @DisplayName("PUT /api/expenses/{id} → 404 เมื่อไม่พบ")
    void update_not_found() throws Exception {
        String body = """
                {"title":"Nope"}
                """;
        mvc.perform(put(BASE + "/{id}", 999999)
                        .with(asAdmin(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isNotFound());
    }

    // -------------------- DELETE --------------------

    @Test
    @DisplayName("DELETE /api/expenses/{id} → 204 เมื่อ ADMIN ลบสำเร็จ")
    void delete_ok_as_admin() throws Exception {
        mvc.perform(delete(BASE + "/{id}", expenseId).with(asAdmin(adminId)))
                .andExpect(status().isNoContent());

        assertThat(expenseRepo.findById(expenseId)).isEmpty();
    }

    @Test
    @DisplayName("DELETE /api/expenses/{id} → 404 เมื่อไม่พบ")
    void delete_not_found() throws Exception {
        mvc.perform(delete(BASE + "/{id}", 999999).with(asAdmin(adminId)))
                .andExpect(status().isNotFound());
    }

    // -------------------- TOTALS & SUMMARY --------------------

    @Test
    @DisplayName("GET /{id}/total-items → 200 สำหรับสมาชิก และเท่ากับผลรวม item จริง")
    void items_total_ok() throws Exception {
        BigDecimal sum = itemRepo.findByExpense_Id(expenseId).stream()
                .map(ExpenseItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2);

        mvc.perform(get(BASE + "/{id}/total-items", expenseId).with(asUser(memberId)))
                .andExpect(status().isOk())
                .andExpect(content().string(sum.toPlainString()));
    }

    @Test
    @DisplayName("GET /{id}/total-verified → 200 และเท่ากับยอด verified payments (ถ้ามี repo)")
    void verified_total_ok() throws Exception {
        var expected = paymentRepo == null
                ? "0" // ถ้าไม่มี repo/feature ให้คาด 0
                : paymentRepo.findByExpense_Id(expenseId).stream()
                .filter(p -> p.getStatus() == PaymentStatus.VERIFIED) // TODO ปรับตามฟิลด์จริง
                .map(ExpensePayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2).toPlainString();

        mvc.perform(get(BASE + "/{id}/total-verified", expenseId).with(asUser(memberId)))
                .andExpect(status().isOk())
                .andExpect(content().string(expected));
    }

    @Test
    @DisplayName("GET /{id}/summary → 200 และมี keys: itemsTotal, verifiedTotal")
    void summary_ok() throws Exception {
        mvc.perform(get(BASE + "/{id}/summary", expenseId).with(asUser(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemsTotal").exists())
                .andExpect(jsonPath("$.verifiedTotal").exists());
    }

    // -------------------- SETTLEMENTS --------------------

    @Test
    @DisplayName("GET /{id}/settlement → 200 (รูปแบบผลลัพธ์ตาม service จริง)")
    void settlement_all_ok() throws Exception {
        mvc.perform(get(BASE + "/{id}/settlement", expenseId).with(asUser(memberId)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /{id}/settlement/{userId} → 200 (รูปแบบผลลัพธ์ตาม service จริง)")
    void settlement_by_user_ok() throws Exception {
        mvc.perform(get(BASE + "/{id}/settlement/{userId}", expenseId, memberId).with(asUser(memberId)))
                .andExpect(status().isOk());
    }
}
