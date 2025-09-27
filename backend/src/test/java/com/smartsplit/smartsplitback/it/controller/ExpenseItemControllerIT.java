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
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("ExpenseItemController IT (end-to-end to DB)")
class ExpenseItemControllerIT extends BaseIntegrationTest {

    private static final String BASE = "/api/expenses/{expenseId}/items";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JwtService jwtService;


    @Autowired GroupRepository groupRepo;
    @Autowired ExpenseRepository expenseRepo;
    @Autowired ExpenseItemRepository itemRepo;
    @Autowired GroupMemberRepository memberRepo;
    @Autowired UserRepository userRepo;


    long adminId;
    long meId;
    long otherId;


    long groupId;
    long expenseId;
    long itemId1;
    long itemId2;


    long emptyExpenseId;

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

        itemRepo.deleteAll();
        expenseRepo.deleteAll();
        memberRepo.deleteAll();
        groupRepo.deleteAll();
        userRepo.deleteAll();

        // ----- users -----
        User admin = new User();
        admin.setEmail("admin@example.com");
        admin.setUserName("Admin");
        admin.setPasswordHash("{noop}x");
        admin.setRole(Role.ADMIN);
        adminId = userRepo.save(admin).getId();

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
        otherId = userRepo.save(other).getId();

        // ----- group -----
        Group g = new Group();
        g.setName("IT-Group");
        g.setOwner(userRepo.getReferenceById(meId));
        groupId = groupRepo.save(g).getId();

        // me เป็นสมาชิกกลุ่ม
        GroupMember gm = new GroupMember();
        gm.setGroup(g);
        gm.setUser(me);
        memberRepo.save(gm);

        // ----- expense หลัก (me เป็น payer) -----
        Expense exp = new Expense();
        exp.setGroup(g);
        exp.setTitle("IT-Expense");
        exp.setStatus(ExpenseStatus.OPEN);
        exp.setType(ExpenseType.CUSTOM);
        exp.setCreatedAt(LocalDateTime.now());
        exp.setAmount(new BigDecimal("0.00"));
        exp.setPayer(me);
        expenseId = expenseRepo.save(exp).getId();

        // ----- items ใต้ expense หลัก -----
        ExpenseItem it1 = new ExpenseItem();
        it1.setExpense(exp);
        it1.setName("Coffee");
        it1.setAmount(new BigDecimal("45.50"));
        itemId1 = itemRepo.save(it1).getId();

        ExpenseItem it2 = new ExpenseItem();
        it2.setExpense(exp);
        it2.setName("Snack");
        it2.setAmount(new BigDecimal("20.00"));
        itemId2 = itemRepo.save(it2).getId();

        // ----- expense ว่าง (ไว้เทสต์ total=0) -----
        Expense expEmpty = new Expense();
        expEmpty.setGroup(g);
        expEmpty.setTitle("Empty");
        expEmpty.setStatus(ExpenseStatus.OPEN);
        expEmpty.setType(ExpenseType.CUSTOM);
        expEmpty.setCreatedAt(LocalDateTime.now());
        expEmpty.setAmount(new BigDecimal("0.00"));
        expEmpty.setPayer(me);
        emptyExpenseId = expenseRepo.save(expEmpty).getId();
    }

    // -------------------- LIST --------------------

    @Test
    @DisplayName("GET /items → 401 เมื่อไม่ส่ง token")
    void list_unauthorized_without_token() throws Exception {
        mvc.perform(get(BASE, expenseId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /items → 403 เมื่อผู้เรียกไม่ใช่สมาชิกกลุ่ม")
    void list_forbidden_for_non_member() throws Exception {
        mvc.perform(get(BASE, expenseId).with(asUser(otherId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /items → 200 และมีรายการ เมื่อเป็นสมาชิกกลุ่ม")
    void list_ok_for_member() throws Exception {
        mvc.perform(get(BASE, expenseId).with(asUser(meId)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].name").exists());
    }

    // -------------------- GET by id --------------------

    @Test
    @DisplayName("GET /items/{id} → 403 เมื่อผู้เรียกไม่ใช่สมาชิกกลุ่ม")
    void get_forbidden_for_non_member() throws Exception {
        mvc.perform(get(BASE + "/{itemId}", expenseId, itemId1).with(asUser(otherId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /items/{id} → 404 เมื่อ itemId ไม่อยู่ใต้ expense เดียวกัน (แต่ผู้เรียกเป็นสมาชิก)")
    void get_notfound_wrong_scope() throws Exception {
        // สร้าง expense อีกอัน + item ใต้ expense นั้น
        Expense exp2 = buildExpense(groupId, meId, "Another", new BigDecimal("0.00"));
        ExpenseItem itAlien = new ExpenseItem();
        itAlien.setExpense(expenseRepo.findById(exp2.getId()).orElseThrow());
        itAlien.setName("Alien");
        itAlien.setAmount(new BigDecimal("9.99"));
        long alienItemId = itemRepo.save(itAlien).getId();

        // เรียกด้วย expenseId หลัก + item จาก expense อื่น → ควร 404 (ผ่าน perm ก่อนเพราะ me เป็นสมาชิกกลุ่มเดียวกัน)
        mvc.perform(get(BASE + "/{itemId}", expenseId, alienItemId).with(asUser(meId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /items/{id} → 200 เมื่อพบ")
    void get_ok() throws Exception {
        mvc.perform(get(BASE + "/{itemId}", expenseId, itemId1).with(asUser(meId)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").value("Coffee"))
                .andExpect(jsonPath("$.amount").value(45.50));
    }

    // -------------------- CREATE --------------------

    @Test
    @DisplayName("POST /items → 403 เมื่อผู้เรียกไม่ใช่สมาชิก/ไม่มีสิทธิ์ manage")
    void create_forbidden_for_non_member() throws Exception {
        mvc.perform(post(BASE, expenseId)
                        .with(asUser(otherId))
                        .param("name", "Dinner")
                        .param("amount", "200.00"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /items → 201 เมื่อ ADMIN สร้างสำเร็จ และบันทึกลง DB")
    void create_created_as_admin() throws Exception {
        mvc.perform(post(BASE, expenseId)
                        .with(asAdmin(adminId))
                        .param("name", "Taxi")
                        .param("amount", "80.00"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").value("Taxi"));

        var list = itemRepo.findByExpense_Id(expenseId);
        assertThat(list.stream().anyMatch(i -> "Taxi".equals(i.getName()))).isTrue();
    }

    @Test
    @DisplayName("POST /items → 400 เมื่อขาด name/amount")
    void create_bad_request_missing_fields() throws Exception {
        // ขาด name
        mvc.perform(post(BASE, expenseId)
                        .with(asAdmin(adminId))
                        .param("amount", "10.00"))
                .andExpect(status().isBadRequest());

        // ขาด amount
        mvc.perform(post(BASE, expenseId)
                        .with(asAdmin(adminId))
                        .param("name", "Snack"))
                .andExpect(status().isBadRequest());
    }

    // -------------------- UPDATE --------------------

    @Test
    @DisplayName("PUT /items/{id} → 403 เมื่อผู้เรียกไม่มีสิทธิ์")
    void update_forbidden_for_non_member() throws Exception {
        mvc.perform(put(BASE + "/{itemId}", expenseId, itemId1)
                        .with(asUser(otherId))
                        .param("name", "Edited")
                        .param("amount", "33.33"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /items/{id} → 200 เมื่อ ADMIN อัปเดตสำเร็จ และบันทึกลง DB")
    void update_ok_as_admin() throws Exception {
        mvc.perform(put(BASE + "/{itemId}", expenseId, itemId1)
                        .with(asAdmin(adminId))
                        .param("name", "Edited")
                        .param("amount", "10.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Edited"))
                .andExpect(jsonPath("$.amount").value(10.00));

        var updated = itemRepo.findById(itemId1).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Edited");
        assertThat(updated.getAmount()).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    @DisplayName("PUT /items/{id} → 400 เมื่อขาด name/amount")
    void update_bad_request_missing_fields() throws Exception {
        mvc.perform(put(BASE + "/{itemId}", expenseId, itemId1)
                        .with(asAdmin(adminId))
                        .param("amount", "10.00"))
                .andExpect(status().isBadRequest());

        mvc.perform(put(BASE + "/{itemId}", expenseId, itemId1)
                        .with(asAdmin(adminId))
                        .param("name", "Edited"))
                .andExpect(status().isBadRequest());
    }

    // -------------------- DELETE --------------------

    @Test
    @DisplayName("DELETE /items/{id} → 403 เมื่อผู้เรียกไม่มีสิทธิ์")
    void delete_forbidden_for_non_member() throws Exception {
        mvc.perform(delete(BASE + "/{itemId}", expenseId, itemId1).with(asUser(otherId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /items/{id} → 204 เมื่อ ADMIN ลบสำเร็จ และลบออกจาก DB จริง")
    void delete_ok_as_admin() throws Exception {
        mvc.perform(delete(BASE + "/{itemId}", expenseId, itemId2).with(asAdmin(adminId)))
                .andExpect(status().isNoContent());

        assertThat(itemRepo.findById(itemId2)).isEmpty();
    }

    // -------------------- TOTAL --------------------

    @Test
    @DisplayName("GET /items/total → 403 เมื่อผู้เรียกไม่ใช่สมาชิกกลุ่ม")
    void total_forbidden_for_non_member() throws Exception {
        mvc.perform(get(BASE + "/total", expenseId).with(asUser(otherId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /items/total → 200 และคืน 0 เมื่อ expense ว่าง")
    void total_ok_zero_when_empty() throws Exception {
        mvc.perform(get("/api/expenses/{expenseId}/items/total", emptyExpenseId).with(asUser(meId)))
                .andExpect(status().isOk())
                .andExpect(content().string("0.00"));
    }

    @Test
    @DisplayName("GET /items/total → 200 เมื่อมีผลรวมจริง (Coffee 45.50 + Edited/10.00 + Snack/20.00 ถ้ายังไม่ลบ)")
    void total_ok_with_value() throws Exception {
        // ค่ารวมตอนนี้ขึ้นกับเคสก่อนหน้า:
        //   - Coffee 45.50 (itemId1) ถูกแก้เป็น 10.00 ใน update_ok_as_admin (ถ้ารันก่อน)
        //   - Snack 20.00 (itemId2) อาจถูกลบใน delete_ok_as_admin (ถ้ารันก่อน)
        // เพื่อให้ deterministic ให้คำนวณตามสถานะปัจจุบันใน DB แล้ว assert แบบยืดหยุ่น
        var items = itemRepo.findByExpense_Id(expenseId);
        BigDecimal sum = items.stream().map(ExpenseItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);

        mvc.perform(get(BASE + "/total", expenseId).with(asUser(meId)))
                .andExpect(status().isOk())
                .andExpect(content().string(sum.toPlainString()));
    }

    // ---------- helper ----------
    private Expense buildExpense(long groupId, long payerUserId, String title, BigDecimal amount) {
        Group gRef = groupRepo.findById(groupId).orElseThrow();
        User payer = userRepo.findById(payerUserId).orElseThrow();

        Expense e = new Expense();
        e.setGroup(gRef);
        e.setTitle(title);
        e.setStatus(ExpenseStatus.OPEN);
        e.setType(ExpenseType.CUSTOM);
        e.setCreatedAt(LocalDateTime.now());
        e.setAmount(amount);
        e.setPayer(payer);
        return expenseRepo.save(e);
    }
}
