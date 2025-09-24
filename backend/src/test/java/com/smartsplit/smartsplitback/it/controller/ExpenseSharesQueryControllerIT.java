
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("ExpenseSharesQueryController IT (FULL stack ผ่าน DB จริง)")
class ExpenseSharesQueryControllerIT extends BaseIntegrationTest {

    private static final String BASE = "/api/expenses/{expenseId}/shares";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JwtService jwtService;

    @Autowired UserRepository userRepository;
    @Autowired GroupRepository groupRepository;
    @Autowired GroupMemberRepository groupMemberRepository;
    @Autowired ExpenseRepository expenseRepository;
    @Autowired ExpenseItemRepository expenseItemRepository;
    @Autowired ExpenseItemShareRepository expenseItemShareRepository;

    long meId;
    long otherId;
    long outsiderId;
    long groupId;
    long expenseId;

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
    void setUp() {

        expenseItemShareRepository.deleteAll();
        expenseItemRepository.deleteAll();
        expenseRepository.deleteAll();
        groupMemberRepository.deleteAll();
        groupRepository.deleteAll();
        userRepository.deleteAll();


        User me = new User();
        me.setEmail("me@example.com");
        me.setUserName("Me");
        me.setPasswordHash("{noop}x");
        me.setRole(Role.USER);
        meId = userRepository.save(me).getId();

        User other = new User();
        other.setEmail("other@example.com");
        other.setUserName("Other");
        other.setPasswordHash("{noop}x");
        other.setRole(Role.USER);
        otherId = userRepository.save(other).getId();

        User outsider = new User();
        outsider.setEmail("outsider@example.com");
        outsider.setUserName("Out");
        outsider.setPasswordHash("{noop}x");
        outsider.setRole(Role.USER);
        outsiderId = userRepository.save(outsider).getId();

        // ---- group  ----
        Group g = new Group();
        g.setName("IT Test Group");
        g.setOwner(me);                
        g = groupRepository.save(g);
        groupId = g.getId();

        // ---- group members (me + other) ----
        GroupMember gm1 = new GroupMember();
        gm1.setGroup(g);
        gm1.setUser(me);
        groupMemberRepository.save(gm1);

        GroupMember gm2 = new GroupMember();
        gm2.setGroup(g);
        gm2.setUser(other);
        groupMemberRepository.save(gm2);

        // ---- expense ----
        Expense exp = new Expense();
        exp.setGroup(g);
        exp.setTitle("Dinner");
        exp.setStatus(ExpenseStatus.OPEN);
        exp.setType(ExpenseType.EQUAL);
       
        exp.setAmount(new BigDecimal("300.00"));
        exp.setPayer(me);
        exp = expenseRepository.save(exp);
        expenseId = exp.getId();

        // ---- expense items ----
        ExpenseItem item1 = new ExpenseItem();
        item1.setExpense(exp);
        item1.setName("Pizza");
        item1.setAmount(new BigDecimal("200.00"));
        item1 = expenseItemRepository.save(item1);

        ExpenseItem item2 = new ExpenseItem();
        item2.setExpense(exp);
        item2.setName("Drinks");
        item2.setAmount(new BigDecimal("100.00"));
        item2 = expenseItemRepository.save(item2);

        // ---- shares (ใช้ shareValue/sharePercent ) ----
        ExpenseItemShare s1 = new ExpenseItemShare();
        s1.setExpenseItem(item1);
        s1.setParticipant(me);
        s1.setShareValue(new BigDecimal("120.00"));
        expenseItemShareRepository.save(s1);

        ExpenseItemShare s2 = new ExpenseItemShare();
        s2.setExpenseItem(item1);
        s2.setParticipant(other);
        s2.setShareValue(new BigDecimal("80.00"));
        expenseItemShareRepository.save(s2);

        ExpenseItemShare s3 = new ExpenseItemShare();
        s3.setExpenseItem(item2);
        s3.setParticipant(me);
        s3.setShareValue(new BigDecimal("60.00"));
        expenseItemShareRepository.save(s3);

        ExpenseItemShare s4 = new ExpenseItemShare();
        s4.setExpenseItem(item2);
        s4.setParticipant(other);
        s4.setShareValue(new BigDecimal("40.00"));
        expenseItemShareRepository.save(s4);

        assertThat(expenseItemShareRepository.findAll()).hasSize(4);
    }

    // -------------------- LIST (GET /shares) --------------------

    @Test
    @DisplayName("401: ไม่ส่ง Authorization header → ถูกบล็อกก่อนเข้า controller")
    void list_unauthorized_without_token() throws Exception {
        mvc.perform(get(BASE, expenseId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("403: ผู้ใช้นอกกลุ่ม → Forbidden")
    void list_forbidden_when_not_group_member() throws Exception {
        mvc.perform(get(BASE, expenseId).with(asUser(outsiderId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("200: สมาชิกกลุ่มดู /shares ได้ → 200 + JSON")
    void list_ok_when_group_member() throws Exception {
        mvc.perform(get(BASE, expenseId).with(asUser(meId)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
        mvc.perform(get(BASE, expenseId).with(asUser(otherId)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    // -------------------- LIST MINE (GET /shares/mine) --------------------

    @Test
    @DisplayName("401: /mine ไม่ส่ง token → Unauthorized")
    void mine_unauthorized_without_token() throws Exception {
        mvc.perform(get(BASE + "/mine", expenseId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("403: /mine เมื่อผู้ใช้ไม่อยู่ในกลุ่ม → Forbidden")
    void mine_forbidden_when_not_group_member() throws Exception {
        mvc.perform(get(BASE + "/mine", expenseId).with(asUser(outsiderId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("200: /mine ของสมาชิก (me) → ได้เฉพาะส่วนของตัวเอง")
    void mine_ok_when_member_gets_own_shares() throws Exception {
        mvc.perform(get(BASE + "/mine", expenseId).with(asUser(meId)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("200: /mine ของสมาชิก (other)")
    void mine_ok_when_other_gets_own_shares() throws Exception {
        mvc.perform(get(BASE + "/mine", expenseId).with(asUser(otherId)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }
}
