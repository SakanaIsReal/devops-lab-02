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

import static java.math.RoundingMode.HALF_UP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("ExpenseItemController IT (end-to-end to DB, FX-locked)")
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
    long itemIdThb;
    long itemIdUsd;

    long emptyExpenseId;

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

        Group g = new Group();
        g.setName("IT-Group");
        g.setOwner(userRepo.getReferenceById(meId));
        groupId = groupRepo.save(g).getId();

        GroupMember gm = new GroupMember();
        gm.setGroup(g);
        gm.setUser(me);
        memberRepo.save(gm);

        Expense exp = new Expense();
        exp.setGroup(g);
        exp.setTitle("IT-Expense");
        exp.setStatus(ExpenseStatus.OPEN);
        exp.setType(ExpenseType.CUSTOM);
        exp.setCreatedAt(LocalDateTime.now());
        exp.setAmount(new BigDecimal("0.00"));
        exp.setPayer(me);
        exp.setExchangeRatesJson("{\"THB\":1,\"USD\":36.25,\"JPY\":0.245,\"EUR\":39.90}");
        expenseId = expenseRepo.save(exp).getId();

        ExpenseItem itThb = new ExpenseItem();
        itThb.setExpense(exp);
        itThb.setName("Coffee");
        itThb.setAmount(new BigDecimal("45.50"));
        itThb.setCurrency("THB");
        itemIdThb = itemRepo.save(itThb).getId();

        ExpenseItem itUsd = new ExpenseItem();
        itUsd.setExpense(exp);
        itUsd.setName("Snack");
        itUsd.setAmount(new BigDecimal("3.00"));
        itUsd.setCurrency("USD");
        itemIdUsd = itemRepo.save(itUsd).getId();

        Expense expEmpty = new Expense();
        expEmpty.setGroup(g);
        expEmpty.setTitle("Empty");
        expEmpty.setStatus(ExpenseStatus.OPEN);
        expEmpty.setType(ExpenseType.CUSTOM);
        expEmpty.setCreatedAt(LocalDateTime.now());
        expEmpty.setAmount(new BigDecimal("0.00"));
        expEmpty.setPayer(me);
        expEmpty.setExchangeRatesJson("{\"THB\":1,\"USD\":36.25}");
        emptyExpenseId = expenseRepo.save(expEmpty).getId();
    }

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
    @DisplayName("GET /items → 200 สำหรับสมาชิก และ amountThb คำนวณตามเรตที่ล็อก")
    void list_ok_for_member_with_amountThb() throws Exception {
        BigDecimal coffeeThb = new BigDecimal("45.50");
        BigDecimal snackThb = new BigDecimal("3.00").multiply(new BigDecimal("36.25")).setScale(2, HALF_UP);

        mvc.perform(get(BASE, expenseId).with(asUser(meId)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[?(@.name=='Coffee')].amount").value(45.50))
                .andExpect(jsonPath("$[?(@.name=='Coffee')].currency").value("THB"))
                .andExpect(jsonPath("$[?(@.name=='Coffee')].amountThb").value(coffeeThb.doubleValue()))
                .andExpect(jsonPath("$[?(@.name=='Snack')].amount").value(3.00))
                .andExpect(jsonPath("$[?(@.name=='Snack')].currency").value("USD"))
                .andExpect(jsonPath("$[?(@.name=='Snack')].amountThb").value(snackThb.doubleValue()));
    }

    @Test
    @DisplayName("GET /items/{id} → 403 เมื่อผู้เรียกไม่ใช่สมาชิกกลุ่ม")
    void get_forbidden_for_non_member() throws Exception {
        mvc.perform(get(BASE + "/{itemId}", expenseId, itemIdThb).with(asUser(otherId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /items/{id} → 403 เมื่อ itemId ไม่อยู่ใต้ expense เดียวกัน (wrong scope)")
    void get_notfound_wrong_scope() throws Exception {
        Expense exp2 = new Expense();
        exp2.setGroup(groupRepo.findById(groupId).orElseThrow());
        exp2.setTitle("Another");
        exp2.setStatus(ExpenseStatus.OPEN);
        exp2.setType(ExpenseType.CUSTOM);
        exp2.setCreatedAt(LocalDateTime.now());
        exp2.setAmount(new BigDecimal("0.00"));
        exp2.setPayer(userRepo.findById(meId).orElseThrow());
        exp2.setExchangeRatesJson("{\"THB\":1,\"USD\":36.25}");
        long exp2Id = expenseRepo.save(exp2).getId();

        ExpenseItem itAlien = new ExpenseItem();
        itAlien.setExpense(expenseRepo.findById(exp2Id).orElseThrow());
        itAlien.setName("Alien");
        itAlien.setAmount(new BigDecimal("9.99"));
        itAlien.setCurrency("THB");
        long alienItemId = itemRepo.save(itAlien).getId();

        mvc.perform(get(BASE + "/{itemId}", expenseId, alienItemId).with(asUser(meId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /items/{id} → 200 เมื่อพบ และระบุ amountThb ถูกต้อง")
    void get_ok() throws Exception {
        BigDecimal coffeeThb = new BigDecimal("45.50");
        mvc.perform(get(BASE + "/{itemId}", expenseId, itemIdThb).with(asUser(meId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Coffee"))
                .andExpect(jsonPath("$.amount").value(45.50))
                .andExpect(jsonPath("$.currency").value("THB"))
                .andExpect(jsonPath("$.amountThb").value(coffeeThb.doubleValue()));
    }

    @Test
    @DisplayName("POST /items → 403 เมื่อผู้เรียกไม่มีสิทธิ์ manage")
    void create_forbidden_for_non_member() throws Exception {
        mvc.perform(post(BASE, expenseId)
                        .with(asUser(otherId))
                        .param("name", "Dinner")
                        .param("amount", "200.00"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /items → 201 ADMIN: default currency=THB และ amountThb = amount")
    void create_created_as_admin_thb_default() throws Exception {
        mvc.perform(post(BASE, expenseId)
                        .with(asAdmin(adminId))
                        .param("name", "Taxi")
                        .param("amount", "80.00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Taxi"))
                .andExpect(jsonPath("$.currency").value("THB"))
                .andExpect(jsonPath("$.amount").value(80.00))
                .andExpect(jsonPath("$.amountThb").value(80.00));

        var list = itemRepo.findByExpense_Id(expenseId);
        assertThat(list.stream().anyMatch(i -> "Taxi".equals(i.getName()) && "THB".equals(i.getCurrency()))).isTrue();
    }

    @Test
    @DisplayName("POST /items → 201 ADMIN: currency=USD (case-insensitive) คำนวณ amountThb ตามเรต")
    void create_created_as_admin_usd_uppercase() throws Exception {
        BigDecimal usd = new BigDecimal("2.50");
        BigDecimal thb = usd.multiply(new BigDecimal("36.25")).setScale(2, HALF_UP);

        mvc.perform(post(BASE, expenseId)
                        .with(asAdmin(adminId))
                        .param("name", "Burger")
                        .param("amount", usd.toPlainString())
                        .param("currency", "usd"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Burger"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.amount").value(usd.doubleValue()))
                .andExpect(jsonPath("$.amountThb").value(thb.doubleValue()));
    }

    @Test
    @DisplayName("POST /items → 400 เมื่อขาด name/amount")
    void create_bad_request_missing_fields() throws Exception {
        mvc.perform(post(BASE, expenseId)
                        .with(asAdmin(adminId))
                        .param("amount", "10.00"))
                .andExpect(status().isBadRequest());

        mvc.perform(post(BASE, expenseId)
                        .with(asAdmin(adminId))
                        .param("name", "Snack"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /items/{id} → 403 เมื่อผู้เรียกไม่มีสิทธิ์")
    void update_forbidden_for_non_member() throws Exception {
        mvc.perform(put(BASE + "/{itemId}", expenseId, itemIdThb)
                        .with(asUser(otherId))
                        .param("name", "Edited")
                        .param("amount", "33.33"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /items/{id} → 200 ADMIN: แก้ชื่อ/จำนวน/สกุลเงิน และ amountThb เปลี่ยนตาม")
    void update_ok_as_admin_change_fields() throws Exception {
        ExpenseItem tmp = new ExpenseItem();
        tmp.setExpense(expenseRepo.findById(expenseId).orElseThrow());
        tmp.setName("Temp");
        tmp.setAmount(new BigDecimal("1.00"));
        tmp.setCurrency("THB");
        long tmpId = itemRepo.save(tmp).getId();

        BigDecimal newAmount = new BigDecimal("4.20");
        BigDecimal expectedThb = newAmount.multiply(new BigDecimal("39.90")).setScale(2, HALF_UP);

        mvc.perform(put(BASE + "/{itemId}", expenseId, tmpId)
                        .with(asAdmin(adminId))
                        .param("name", "Edited+EUR")
                        .param("amount", newAmount.toPlainString())
                        .param("currency", "EUR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Edited+EUR"))
                .andExpect(jsonPath("$.amount").value(newAmount.doubleValue()))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.amountThb").value(expectedThb.doubleValue()));

        var updated = itemRepo.findById(tmpId).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Edited+EUR");
        assertThat(updated.getAmount()).isEqualByComparingTo(newAmount);
        assertThat(updated.getCurrency()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("PUT /items/{id} → 400 เมื่อขาด name/amount (controller บังคับ full update)")
    void update_bad_request_missing_fields() throws Exception {
        mvc.perform(put(BASE + "/{itemId}", expenseId, itemIdThb)
                        .with(asAdmin(adminId))
                        .param("amount", "10.00"))
                .andExpect(status().isBadRequest());

        mvc.perform(put(BASE + "/{itemId}", expenseId, itemIdThb)
                        .with(asAdmin(adminId))
                        .param("name", "Edited"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /items/{id} → 403 เมื่อผู้เรียกไม่มีสิทธิ์")
    void delete_forbidden_for_non_member() throws Exception {
        mvc.perform(delete(BASE + "/{itemId}", expenseId, itemIdUsd).with(asUser(otherId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /items/{id} → 204 ADMIN: ลบสำเร็จและหายจาก DB")
    void delete_ok_as_admin() throws Exception {
        ExpenseItem toDelete = new ExpenseItem();
        toDelete.setExpense(expenseRepo.findById(expenseId).orElseThrow());
        toDelete.setName("Del");
        toDelete.setAmount(new BigDecimal("7.77"));
        toDelete.setCurrency("THB");
        long delId = itemRepo.save(toDelete).getId();

        mvc.perform(delete(BASE + "/{itemId}", expenseId, delId).with(asAdmin(adminId)))
                .andExpect(status().isNoContent());

        assertThat(itemRepo.findById(delId)).isEmpty();
    }

    @Test
    @DisplayName("GET /items/total → 403 เมื่อผู้เรียกไม่ใช่สมาชิกกลุ่ม")
    void total_forbidden_for_non_member() throws Exception {
        mvc.perform(get(BASE + "/total", expenseId).with(asUser(otherId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /items/total → 200 และคืน 0.00 เมื่อ expense ว่าง")
    void total_ok_zero_when_empty() throws Exception {
        mvc.perform(get("/api/expenses/{expenseId}/items/total", emptyExpenseId).with(asUser(meId)))
                .andExpect(status().isOk())
                .andExpect(content().string("0.00"));
    }

    @Test
    @DisplayName("GET /items/total → 200 รวม THB จากหลายสกุลตามเรตล็อก (THB + USD)")
    void total_ok_with_value_fx_locked() throws Exception {
        var items = itemRepo.findByExpense_Id(expenseId);

        BigDecimal thb = items.stream()
                .filter(i -> "THB".equals(i.getCurrency()) || i.getCurrency() == null)
                .map(ExpenseItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal usd = items.stream()
                .filter(i -> "USD".equals(i.getCurrency()))
                .map(ExpenseItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalThb = thb
                .add(usd.multiply(new BigDecimal("36.25")))
                .setScale(2, HALF_UP);

        mvc.perform(get(BASE + "/total", expenseId).with(asUser(meId)))
                .andExpect(status().isOk())
                .andExpect(content().string(totalThb.toPlainString()));
    }
}
