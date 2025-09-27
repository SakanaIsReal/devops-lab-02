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
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@DisplayName("ExpenseItemShareController IT (end-to-end to DB)")
class ExpenseItemShareControllerIT extends BaseIntegrationTest {

    private static final String BASE = "/api/expenses/{expenseId}/items/{itemId}/shares";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JwtService jwtService;

    
    @Autowired GroupRepository groupRepo;
    @Autowired ExpenseRepository expenseRepo;
    @Autowired ExpenseItemRepository itemRepo;
    @Autowired ExpenseItemShareRepository shareRepo;
    @Autowired GroupMemberRepository memberRepo;
    @Autowired UserRepository userRepo;


    long adminId;
    long meId;
    long otherId;

    long groupId;
    long expenseId;
    long itemId;


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

        shareRepo.deleteAll();
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

        // ----- expense -----
        Expense exp = new Expense();
        exp.setGroup(g);

        exp.setTitle("IT-Expense");
        exp.setStatus(ExpenseStatus.OPEN);
        exp.setType(ExpenseType.CUSTOM);
        exp.setCreatedAt(LocalDateTime.now());
        exp.setPayer(me);
        expenseId = expenseRepo.save(exp).getId();


        ExpenseItem it = new ExpenseItem();
        it.setExpense(exp);
        it.setName("Item-1");
        it.setAmount(new BigDecimal("100.00"));
        itemId = itemRepo.save(it).getId();


        GroupMember m1 = new GroupMember();
        m1.setGroup(g);
        m1.setUser(me);
        memberRepo.save(m1);

        GroupMember m2 = new GroupMember();
        m2.setGroup(g);
        m2.setUser(other);
        memberRepo.save(m2);
    }

    // -------------------- READ --------------------

    @Test
    @DisplayName("GET /shares → 200 (ลิสต์อาจว่าง) เมื่อมีสิทธิ์ (ใช้ ADMIN)")
    void list_ok_initially_empty() throws Exception {
        mvc.perform(get(BASE, expenseId, itemId).with(asAdmin(adminId)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    // -------------------- ADD --------------------

    @Test
    @DisplayName("POST /shares → 400 เมื่อไม่ส่ง shareValue และ sharePercent")
    void add_bad_request_missing_both() throws Exception {
        mvc.perform(post(BASE, expenseId, itemId)
                        .with(asAdmin(adminId))
                        .param("participantUserId", String.valueOf(otherId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /shares (shareValue) → 200 และถูกบันทึกจริง")
    void add_ok_with_value_persists() throws Exception {
        mvc.perform(post(BASE, expenseId, itemId)
                        .with(asAdmin(adminId))
                        .param("participantUserId", String.valueOf(otherId))
                        .param("shareValue", "15.50"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        var list = shareRepo.findByExpenseItem_Id(itemId);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getParticipant().getId()).isEqualTo(otherId);
        assertThat(list.get(0).getShareValue()).isEqualByComparingTo(new BigDecimal("15.50"));
        assertThat(list.get(0).getSharePercent()).isNull(); // บันทึก value ตรง ๆ
    }

    @Test
    @DisplayName("POST /shares (sharePercent=10) → 200 และ value=10% ของ 100 = 10.00")
    void add_ok_with_percent_persists() throws Exception {
        mvc.perform(post(BASE, expenseId, itemId)
                        .with(asAdmin(adminId))
                        .param("participantUserId", String.valueOf(otherId))
                        .param("sharePercent", "10"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        var list = shareRepo.findByExpenseItem_Id(itemId);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getShareValue()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(list.get(0).getSharePercent()).isEqualByComparingTo(new BigDecimal("10"));
    }

    // -------------------- UPDATE --------------------

    @Test
    @DisplayName("PUT /shares/{id} (shareValue) → 200 และอัปเดตจริง")
    void update_ok_with_value_persists() throws Exception {
        // สร้างก่อน 1 รายการ
        var created = shareRepo.save(buildShare(itemId, otherId, new BigDecimal("5.00"), null));

        mvc.perform(put(BASE + "/{shareId}", expenseId, itemId, created.getId())
                        .with(asAdmin(adminId))
                        .param("shareValue", "22.22"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        var updated = shareRepo.findById(created.getId()).orElseThrow();
        assertThat(updated.getShareValue()).isEqualByComparingTo(new BigDecimal("22.22"));
        assertThat(updated.getSharePercent()).isNull();
    }

    @Test
    @DisplayName("PUT /shares/{id} (sharePercent=8) → 200 และ value ควรเป็น 8.00 (ของ 100)")
    void update_ok_with_percent_persists() throws Exception {
        var created = shareRepo.save(buildShare(itemId, otherId, new BigDecimal("5.00"), null));

        mvc.perform(put(BASE + "/{shareId}", expenseId, itemId, created.getId())
                        .with(asAdmin(adminId))
                        .param("sharePercent", "8"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        var updated = shareRepo.findById(created.getId()).orElseThrow();
        assertThat(updated.getShareValue()).isEqualByComparingTo(new BigDecimal("8.00"));
        assertThat(updated.getSharePercent()).isEqualByComparingTo(new BigDecimal("8"));
    }

    @Test
    @DisplayName("PUT /shares/{id} → 404 เมื่อ share ไม่อยู่ใต้ expense/item ที่กำหนด")
    void update_not_found_wrong_scope() throws Exception {
        Group gRef = groupRepo.findById(groupId).orElseThrow();
        User meRef = userRepo.findById(meId).orElseThrow();  // หรือ getReferenceById(meId)

        Expense exp2 = new Expense();
        exp2.setGroup(gRef);
        exp2.setTitle("Another");
        exp2.setStatus(ExpenseStatus.OPEN);
        exp2.setType(ExpenseType.CUSTOM);
        exp2.setCreatedAt(LocalDateTime.now());
        exp2.setAmount(new BigDecimal("50.00"));
        exp2.setPayer(meRef);                                // <-- ใส่ payer ทุกครั้ง
        long exp2Id = expenseRepo.save(exp2).getId();

        ExpenseItem it2 = new ExpenseItem();
        it2.setExpense(exp2);
        it2.setName("OtherItem");
        it2.setAmount(new BigDecimal("50.00"));
        long it2Id = itemRepo.save(it2).getId();

        var alienShare = shareRepo.save(buildShare(it2Id, otherId, new BigDecimal("1.00"), null));

        mvc.perform(put(BASE + "/{shareId}", expenseId, itemId, alienShare.getId())
                        .with(asAdmin(adminId))
                        .param("shareValue", "9.99"))
                .andExpect(status().isForbidden());
    }


    // -------------------- DELETE --------------------

    @Test
    @DisplayName("DELETE /shares/{id} → 204 และลบจริง")
    void delete_ok_persists() throws Exception {
        var created = shareRepo.save(buildShare(itemId, otherId, new BigDecimal("5.00"), null));
        long sid = created.getId();

        mvc.perform(delete(BASE + "/{shareId}", expenseId, itemId, sid)
                        .with(asAdmin(adminId)))
                .andExpect(status().isNoContent());

        assertThat(shareRepo.findById(sid)).isEmpty();
    }

    @Test
    @DisplayName("DELETE /shares/{id} → 404 เมื่อไม่อยู่ใต้ expense/item ที่กำหนด")
    void delete_not_found_wrong_scope() throws Exception {
        Group gRef = groupRepo.findById(groupId).orElseThrow();
        User meRef = userRepo.findById(meId).orElseThrow();

        Expense exp2 = new Expense();
        exp2.setGroup(gRef);
        exp2.setTitle("Another");
        exp2.setStatus(ExpenseStatus.OPEN);
        exp2.setType(ExpenseType.CUSTOM);
        exp2.setCreatedAt(LocalDateTime.now());
        exp2.setAmount(new BigDecimal("50.00"));
        exp2.setPayer(meRef);                                // <-- สำคัญ
        long exp2Id = expenseRepo.save(exp2).getId();

        ExpenseItem it2 = new ExpenseItem();
        it2.setExpense(exp2);
        it2.setName("OtherItem");
        it2.setAmount(new BigDecimal("50.00"));
        long it2Id = itemRepo.save(it2).getId();

        var alienShare = shareRepo.save(buildShare(it2Id, otherId, new BigDecimal("1.00"), null));

        mvc.perform(delete(BASE + "/{shareId}", expenseId, itemId, alienShare.getId())
                        .with(asAdmin(adminId)))
                .andExpect(status().isForbidden());
    }

    // -------- helper --------
    private ExpenseItemShare buildShare(long itemId, long userId, BigDecimal value, BigDecimal percent) {
        ExpenseItem item = itemRepo.findById(itemId).orElseThrow();
        User u = userRepo.findById(userId).orElseThrow();
        ExpenseItemShare s = new ExpenseItemShare();
        s.setExpenseItem(item);
        s.setParticipant(u);
        s.setShareValue(value);
        s.setSharePercent(percent);
        return s;
    }
}
