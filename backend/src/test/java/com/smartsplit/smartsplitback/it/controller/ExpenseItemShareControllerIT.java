package com.smartsplit.smartsplitback.it.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartsplit.smartsplitback.it.security.BaseIntegrationTest;
import com.smartsplit.smartsplitback.model.*;
import com.smartsplit.smartsplitback.repository.*;
import com.smartsplit.smartsplitback.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
    long outsiderId;

    long groupId;
    long expenseId;
    long thbItemId;

    String ratesJson = "{\"THB\":1,\"USD\":36.25,\"JPY\":0.25,\"EUR\":39.50}";

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

        User outsider = new User();
        outsider.setEmail("outsider@example.com");
        outsider.setUserName("Outsider");
        outsider.setPasswordHash("{noop}x");
        outsider.setRole(Role.USER);
        outsiderId = userRepo.save(outsider).getId();

        Group g = new Group();
        g.setName("IT-Group");
        g.setOwner(userRepo.getReferenceById(meId));
        groupId = groupRepo.save(g).getId();

        GroupMember m1 = new GroupMember();
        m1.setGroup(g);
        m1.setUser(userRepo.getReferenceById(meId));
        memberRepo.save(m1);

        GroupMember m2 = new GroupMember();
        m2.setGroup(g);
        m2.setUser(userRepo.getReferenceById(otherId));
        memberRepo.save(m2);

        Expense exp = new Expense();
        exp.setGroup(g);
        exp.setTitle("IT-Expense");
        exp.setStatus(ExpenseStatus.OPEN);
        exp.setType(ExpenseType.CUSTOM);
        exp.setCreatedAt(LocalDateTime.now());
        exp.setPayer(userRepo.getReferenceById(meId));
        exp.setExchangeRatesJson(ratesJson);
        expenseId = expenseRepo.save(exp).getId();

        ExpenseItem itTHB = new ExpenseItem();
        itTHB.setExpense(exp);
        itTHB.setName("Item THB");
        itTHB.setAmount(new BigDecimal("100.00"));
        itTHB.setCurrency("THB");
        thbItemId = itemRepo.save(itTHB).getId();
    }

    private long newItem(String name, String currency, String amount) {
        Expense exp = expenseRepo.findById(expenseId).orElseThrow();
        ExpenseItem it = new ExpenseItem();
        it.setExpense(exp);
        it.setName(name);
        it.setAmount(new BigDecimal(amount));
        it.setCurrency(currency);
        return itemRepo.save(it).getId();
    }

    private Expense newExpenseWithGroup(Group g, User payer, String title) {
        Expense exp = new Expense();
        exp.setGroup(g);
        exp.setTitle(title);
        exp.setStatus(ExpenseStatus.OPEN);
        exp.setType(ExpenseType.CUSTOM);
        exp.setCreatedAt(LocalDateTime.now());
        exp.setPayer(payer);
        exp.setExchangeRatesJson(ratesJson);
        return expenseRepo.save(exp);
    }

    private ExpenseItemShare persistShare(long itemId, long userId, String thb, String percent) {
        ExpenseItemShare s = new ExpenseItemShare();
        s.setExpenseItem(itemRepo.getReferenceById(itemId));
        s.setParticipant(userRepo.getReferenceById(userId));
        s.setShareValue(thb == null ? null : new BigDecimal(thb));
        s.setSharePercent(percent == null ? null : new BigDecimal(percent));
        return shareRepo.save(s);
    }

    @Test
    @DisplayName("GET /shares → 200 (ลิสต์อาจว่าง) เมื่อมีสิทธิ์ (ADMIN)")
    void list_ok_initially_empty() throws Exception {
        mvc.perform(get(BASE, expenseId, thbItemId).with(asAdmin(adminId)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Nested
    @DisplayName("ADD shares • ค่าเงินและเปอร์เซ็นต์")
    class AddShares {

        @Test
        @DisplayName("POST (THB, shareValue=15.50) → 200 และ DB เก็บ THB=15.50, percent=null")
        void add_thb_value() throws Exception {
            mvc.perform(post(BASE, expenseId, thbItemId)
                            .with(asAdmin(adminId))
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("participantUserId", String.valueOf(otherId))
                            .param("shareValue", "15.50"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.shareValue").value(15.50))
                    .andExpect(jsonPath("$.sharePercent").doesNotExist());

            var list = shareRepo.findByExpenseItem_Id(thbItemId);
            assertThat(list).hasSize(1);
            assertThat(list.get(0).getShareValue()).isEqualByComparingTo(new BigDecimal("15.50"));
            assertThat(list.get(0).getSharePercent()).isNull();
        }

        @Test
        @DisplayName("POST (THB, sharePercent=10) → 200 และ DB เก็บ THB=10.00, percent=10")
        void add_thb_percent() throws Exception {
            mvc.perform(post(BASE, expenseId, thbItemId)
                            .with(asAdmin(adminId))
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("participantUserId", String.valueOf(otherId))
                            .param("sharePercent", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sharePercent").value(10.00));

            var list = shareRepo.findByExpenseItem_Id(thbItemId);
            assertThat(list).hasSize(1);
            assertThat(list.get(0).getShareValue()).isEqualByComparingTo(new BigDecimal("10.00"));
            assertThat(list.get(0).getSharePercent()).isEqualByComparingTo(new BigDecimal("10.00"));
        }

        @Test
        @DisplayName("POST (USD, item=123.45, percent=10) → THB = 12.35×36.25 = 447.69")
        void add_usd_percent_converts_to_thb() throws Exception {
            long usdItemId = newItem("Item USD", "USD", "123.45");
            mvc.perform(post(BASE, expenseId, usdItemId)
                            .with(asAdmin(adminId))
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("participantUserId", String.valueOf(otherId))
                            .param("sharePercent", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sharePercent").value(10.00));

            var s = shareRepo.findByExpenseItem_Id(usdItemId).get(0);
            BigDecimal expectedThb = new BigDecimal("12.35").multiply(new BigDecimal("36.25")).setScale(2, HALF_UP);
            assertThat(s.getShareValue()).isEqualByComparingTo(expectedThb);
        }

        @Test
        @DisplayName("POST (USD, shareValue=12.345) → ปัดเป็น 12.35 ก่อนคูณเรต → THB=447.69")
        void add_usd_value_round_and_convert() throws Exception {
            long usdItemId = newItem("Item USD", "USD", "200.00");
            mvc.perform(post(BASE, expenseId, usdItemId)
                            .with(asAdmin(adminId))
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("participantUserId", String.valueOf(otherId))
                            .param("shareValue", "12.345"))
                    .andExpect(status().isOk());

            var s = shareRepo.findByExpenseItem_Id(usdItemId).get(0);
            BigDecimal originalRounded = new BigDecimal("12.35");
            BigDecimal expectedThb = originalRounded.multiply(new BigDecimal("36.25")).setScale(2, HALF_UP);
            assertThat(s.getShareValue()).isEqualByComparingTo(expectedThb);
            assertThat(s.getSharePercent()).isNull();
        }

        @Test
        @DisplayName("POST (JPY, item=800, percent=12.5) → original=100.00 JPY → THB=25.00")
        void add_jpy_percent() throws Exception {
            long jpyItemId = newItem("Item JPY", "JPY", "800.00");
            mvc.perform(post(BASE, expenseId, jpyItemId)
                            .with(asAdmin(adminId))
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("participantUserId", String.valueOf(otherId))
                            .param("sharePercent", "12.5"))
                    .andExpect(status().isOk());

            var s = shareRepo.findByExpenseItem_Id(jpyItemId).get(0);
            BigDecimal expectedThb = new BigDecimal("100.00").multiply(new BigDecimal("0.25")).setScale(2, HALF_UP);
            assertThat(s.getShareValue()).isEqualByComparingTo(expectedThb);
            assertThat(s.getSharePercent()).isEqualByComparingTo(new BigDecimal("12.50"));
        }

        @Test
        @DisplayName("POST → 400 เมื่อไม่ส่งทั้ง shareValue และ sharePercent")
        void add_missing_both() throws Exception {
            mvc.perform(post(BASE, expenseId, thbItemId)
                            .with(asAdmin(adminId))
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("participantUserId", String.valueOf(otherId)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("POST → 400 เมื่อ percent < 0 หรือ > 100 และเมื่อ percent ไม่ใช่ตัวเลข")
        void add_percent_invalid_ranges() throws Exception {
            mvc.perform(post(BASE, expenseId, thbItemId)
                            .with(asAdmin(adminId))
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("participantUserId", String.valueOf(otherId))
                            .param("sharePercent", "-0.01"))
                    .andExpect(status().isBadRequest());

            mvc.perform(post(BASE, expenseId, thbItemId)
                            .with(asAdmin(adminId))
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("participantUserId", String.valueOf(otherId))
                            .param("sharePercent", "100.01"))
                    .andExpect(status().isBadRequest());

            mvc.perform(post(BASE, expenseId, thbItemId)
                            .with(asAdmin(adminId))
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("participantUserId", String.valueOf(otherId))
                            .param("sharePercent", "abc"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("POST → 400 เมื่อขาด participantUserId")
        void add_missing_participant() throws Exception {
            mvc.perform(post(BASE, expenseId, thbItemId)
                            .with(asAdmin(adminId))
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("sharePercent", "10"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("UPDATE shares • เปอร์เซ็นต์และค่าเงิน")
    class UpdateShares {

        @Test
        @DisplayName("PUT (THB, new value=22.22) → 200 และ DB เก็บ THB=22.22, percent=null")
        void update_thb_value() throws Exception {
            var created = persistShare(thbItemId, otherId, "5.00", null);

            mvc.perform(put(BASE + "/{shareId}", expenseId, thbItemId, created.getId())
                            .with(asAdmin(adminId))
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("shareValue", "22.22"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.shareValue").value(22.22));

            var updated = shareRepo.findById(created.getId()).orElseThrow();
            assertThat(updated.getShareValue()).isEqualByComparingTo(new BigDecimal("22.22"));
            assertThat(updated.getSharePercent()).isNull();
        }

        @Test
        @DisplayName("PUT (THB, new percent=8) → 200 และ DB เก็บ THB=8.00, percent=8")
        void update_thb_percent() throws Exception {
            var created = persistShare(thbItemId, otherId, "5.00", null);

            mvc.perform(put(BASE + "/{shareId}", expenseId, thbItemId, created.getId())
                            .with(asAdmin(adminId))
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("sharePercent", "8"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sharePercent").value(8.00));

            var updated = shareRepo.findById(created.getId()).orElseThrow();
            assertThat(updated.getShareValue()).isEqualByComparingTo(new BigDecimal("8.00"));
            assertThat(updated.getSharePercent()).isEqualByComparingTo(new BigDecimal("8.00"));
        }

        @Test
        @DisplayName("PUT (USD, item=200, value=12.345) → THB=12.35×36.25=447.69")
        void update_usd_value_convert() throws Exception {
            long usdItemId = newItem("To Update USD", "USD", "200.00");
            var created = persistShare(usdItemId, otherId, "1.00", null);

            mvc.perform(put(BASE + "/{shareId}", expenseId, usdItemId, created.getId())
                            .with(asAdmin(adminId))
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("shareValue", "12.345"))
                    .andExpect(status().isOk());

            var updated = shareRepo.findById(created.getId()).orElseThrow();
            BigDecimal expectedThb = new BigDecimal("12.35").multiply(new BigDecimal("36.25")).setScale(2, HALF_UP);
            assertThat(updated.getShareValue()).isEqualByComparingTo(expectedThb);
            assertThat(updated.getSharePercent()).isNull();
        }

        @Test
        @DisplayName("PUT (missing both value & percent) → 400")
        void update_missing_both() throws Exception {
            var created = persistShare(thbItemId, otherId, "1.00", null);

            mvc.perform(put(BASE + "/{shareId}", expenseId, thbItemId, created.getId())
                            .with(asAdmin(adminId))
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("PUT → 400 เมื่อ percent < 0 หรือ > 100 และเมื่อ value/percent ไม่ใช่ตัวเลข")
        void update_invalid_ranges_and_types() throws Exception {
            var created = persistShare(thbItemId, otherId, "1.00", null);

            mvc.perform(put(BASE + "/{shareId}", expenseId, thbItemId, created.getId())
                            .with(asAdmin(adminId))
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("sharePercent", "-0.01"))
                    .andExpect(status().isBadRequest());

            mvc.perform(put(BASE + "/{shareId}", expenseId, thbItemId, created.getId())
                            .with(asAdmin(adminId))
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("sharePercent", "100.01"))
                    .andExpect(status().isBadRequest());

            mvc.perform(put(BASE + "/{shareId}", expenseId, thbItemId, created.getId())
                            .with(asAdmin(adminId))
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("shareValue", "xyz"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("PUT → 403 เมื่อ share ไม่อยู่ใต้ expense/item ที่กำหนด (ถูกปฏิเสธก่อนถึง service)")
        void update_wrong_scope_403() throws Exception {
            Group g = groupRepo.findById(groupId).orElseThrow();
            User payer = userRepo.findById(meId).orElseThrow();
            Expense otherExp = newExpenseWithGroup(g, payer, "Other");
            ExpenseItem it2 = new ExpenseItem();
            it2.setExpense(otherExp);
            it2.setName("OtherItem");
            it2.setAmount(new BigDecimal("50.00"));
            it2.setCurrency("THB");
            long it2Id = itemRepo.save(it2).getId();

            var alienShare = persistShare(it2Id, otherId, "1.00", null);

            mvc.perform(put(BASE + "/{shareId}", expenseId, thbItemId, alienShare.getId())
                            .with(asAdmin(adminId))
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("shareValue", "9.99"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("DELETE shares • ขอบเขตและผลกระทบ DB")
    class DeleteShares {

        @Test
        @DisplayName("DELETE → 204 และลบจริง")
        void delete_ok() throws Exception {
            var created = persistShare(thbItemId, otherId, "5.00", null);
            long sid = created.getId();

            mvc.perform(delete(BASE + "/{shareId}", expenseId, thbItemId, sid)
                            .with(asAdmin(adminId)))
                    .andExpect(status().isNoContent());

            assertThat(shareRepo.findById(sid)).isEmpty();
        }

        @Test
        @DisplayName("DELETE → 403 เมื่อ share ไม่อยู่ใต้ expense/item ที่กำหนด (ถูกปฏิเสธก่อนถึง service)")
        void delete_wrong_scope_403() throws Exception {
            Group g = groupRepo.findById(groupId).orElseThrow();
            User payer = userRepo.findById(meId).orElseThrow();
            Expense otherExp = newExpenseWithGroup(g, payer, "Other");
            ExpenseItem it2 = new ExpenseItem();
            it2.setExpense(otherExp);
            it2.setName("OtherItem");
            it2.setAmount(new BigDecimal("50.00"));
            it2.setCurrency("THB");
            long it2Id = itemRepo.save(it2).getId();

            var alienShare = persistShare(it2Id, otherId, "1.00", null);

            mvc.perform(delete(BASE + "/{shareId}", expenseId, thbItemId, alienShare.getId())
                            .with(asAdmin(adminId)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("SECURITY • สิทธิ์เข้าถึง")
    class SecurityCases {

        @Test
        @DisplayName("USER outsider (นอกกลุ่ม) เรียก GET → 403")
        void list_forbidden_for_outsider() throws Exception {
            mvc.perform(get(BASE, expenseId, thbItemId).with(asUser(outsiderId)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("USER outsider เรียก POST → 403 (ไม่มีสิทธิ์ manage)")
        void add_forbidden_for_outsider() throws Exception {
            mvc.perform(post(BASE, expenseId, thbItemId)
                            .with(asUser(outsiderId))
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("participantUserId", String.valueOf(otherId))
                            .param("sharePercent", "10"))
                    .andExpect(status().isForbidden());
        }
    }
}
