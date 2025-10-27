package com.smartsplit.smartsplitback.it.controller;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.math.RoundingMode.HALF_UP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Expense + Item + Share IT (FX locked, ISP, multi-currency, access control)")
class ExpenseControllerIT extends BaseIntegrationTest {

    private static final String BASE_EXP = "/api/expenses";
    private static final String BASE_ITEMS = "/api/expenses/{eid}/items";
    private static final String BASE_SHARES = "/api/expenses/{eid}/items/{iid}/shares";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JwtService jwtService;

    @Autowired GroupRepository groupRepo;
    @Autowired ExpenseRepository expenseRepo;
    @Autowired ExpenseItemRepository itemRepo;
    @Autowired ExpenseItemShareRepository shareRepo;
    @Autowired GroupMemberRepository memberRepo;
    @Autowired UserRepository userRepo;
    @Autowired(required = false) ExpensePaymentRepository paymentRepo;

    long adminId;
    long memberId;
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
    private RequestPostProcessor asAdmin(long id) {
        return req -> { req.addHeader("Authorization", "Bearer " + jwtFor(id, JwtService.ROLE_ADMIN)); return req; };
    }

    @BeforeEach
    void setUp() {
        if (paymentRepo != null) paymentRepo.deleteAll();
        shareRepo.deleteAll();
        itemRepo.deleteAll();
        expenseRepo.deleteAll();
        memberRepo.deleteAll();
        groupRepo.deleteAll();
        userRepo.deleteAll();

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

        var g = new Group();
        g.setName("E2E-Group");
        g.setOwner(userRepo.getReferenceById(memberId));
        groupId = groupRepo.save(g).getId();

        var gm = new GroupMember();
        gm.setGroup(g);
        gm.setUser(mem);
        memberRepo.save(gm);

        var e = new Expense();
        e.setGroup(g);
        e.setPayer(mem);
        e.setAmount(new BigDecimal("0.00"));
        e.setType(ExpenseType.CUSTOM);
        e.setTitle("Team Lunch");
        e.setStatus(ExpenseStatus.OPEN);
        e.setExchangeRatesJson("{\"THB\":1,\"USD\":36.25,\"JPY\":0.245,\"EUR\":39.90,\"GBP\":46.50}");
        expenseId = expenseRepo.save(e).getId();

        var it1 = new ExpenseItem();
        it1.setExpense(e);
        it1.setName("Noodles");
        it1.setAmount(new BigDecimal("150.00"));
        it1.setCurrency("THB");
        itemRepo.save(it1);

        var it2 = new ExpenseItem();
        it2.setExpense(e);
        it2.setName("Drinks");
        it2.setAmount(new BigDecimal("3.50"));
        it2.setCurrency("USD");
        itemRepo.save(it2);

        var it3 = new ExpenseItem();
        it3.setExpense(e);
        it3.setName("Dessert");
        it3.setAmount(new BigDecimal("800"));
        it3.setCurrency("JPY");
        itemRepo.save(it3);

        if (paymentRepo != null) {
            var p1 = new ExpensePayment();
            p1.setExpense(e);
            p1.setAmount(new BigDecimal("30.00"));
            p1.setStatus(PaymentStatus.VERIFIED);
            p1.setFromUser(mem);
            paymentRepo.save(p1);

            var p2 = new ExpensePayment();
            p2.setExpense(e);
            p2.setAmount(new BigDecimal("20.00"));
            p2.setStatus(PaymentStatus.PENDING);
            p2.setFromUser(mem);
            paymentRepo.save(p2);

            var p3 = new ExpensePayment();
            p3.setExpense(e);
            p3.setAmount(new BigDecimal("10.00"));
            p3.setStatus(PaymentStatus.VERIFIED);
            p3.setFromUser(mem);
            paymentRepo.save(p3);
        }
    }

    @Test
    @DisplayName("ADMIN: GET /api/expenses → 200, USER → 403")
    void list_admin_only() throws Exception {
        mvc.perform(get(BASE_EXP).with(asAdmin(adminId)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        mvc.perform(get(BASE_EXP).with(asUser(memberId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("สมาชิกกลุ่ม GET /{id} → 200, คนนอก → 403, ไม่พบ → 404")
    void get_access_variants() throws Exception {
        mvc.perform(get(BASE_EXP + "/{id}", expenseId).with(asUser(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) expenseId))
                .andExpect(jsonPath("$.title").value("Team Lunch"));

        mvc.perform(get(BASE_EXP + "/{id}", expenseId).with(asUser(outsiderId)))
                .andExpect(status().isForbidden());

        mvc.perform(get(BASE_EXP + "/{id}", 999999L).with(asAdmin(adminId)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /group/{groupId} → สมาชิก 200, คนนอก 403")
    void listByGroup_member_only() throws Exception {
        mvc.perform(get(BASE_EXP + "/group/{gid}", groupId).with(asUser(memberId)))
                .andExpect(status().isOk());

        mvc.perform(get(BASE_EXP + "/group/{gid}", groupId).with(asUser(outsiderId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("สร้าง item ผ่าน /items (admin), ตรวจ list + amountThb (USD/JPY/THB), ตรวจ total และ summary (round ต่อรายการ)")
    void item_crud_and_totals_multi_currency() throws Exception {
        var resUsd = mvc.perform(post(BASE_ITEMS, expenseId).with(asAdmin(adminId))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name","Tip")
                        .param("amount","1.235")
                        .param("currency","usd"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode usdNode = om.readTree(resUsd.getResponse().getContentAsString());
        long usdItemId = usdNode.get("id").asLong();
        assertThat(usdNode.get("currency").asText()).isEqualTo("USD"); // uppercase หลัง controller แปลง
        BigDecimal usdAmtRounded = new BigDecimal("1.24");
        BigDecimal usdThb = usdAmtRounded.multiply(new BigDecimal("36.25")).setScale(2, HALF_UP);
        assertThat(new BigDecimal(usdNode.get("amountThb").asText())).isEqualByComparingTo(usdThb);

        var resGbp = mvc.perform(post(BASE_ITEMS, expenseId).with(asAdmin(adminId))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name","Snacks UK")
                        .param("amount","2.50")
                        .param("currency","GBP"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode gbpNode = om.readTree(resGbp.getResponse().getContentAsString());
        long gbpItemId = gbpNode.get("id").asLong();
        BigDecimal gbpThb = new BigDecimal("2.50").multiply(new BigDecimal("46.50")).setScale(2, HALF_UP);
        assertThat(new BigDecimal(gbpNode.get("amountThb").asText())).isEqualByComparingTo(gbpThb);

        var listRes = mvc.perform(get(BASE_ITEMS, expenseId).with(asUser(memberId)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode arr = om.readTree(listRes.getResponse().getContentAsString());
        assertThat(arr.isArray()).isTrue();
        List<JsonNode> nodes = arr.findValues("id");
        assertThat(nodes.stream().map(JsonNode::asLong)).contains(usdItemId, gbpItemId);

        BigDecimal total = BigDecimal.ZERO;
        for (JsonNode n : arr) {
            BigDecimal thb = new BigDecimal(n.get("amountThb").asText());
            total = total.add(thb);
        }
        total = total.setScale(2, HALF_UP);

        mvc.perform(get(BASE_ITEMS + "/total", expenseId).with(asUser(memberId)))
                .andExpect(status().isOk())
                .andExpect(content().string(total.toPlainString()));

        var summaryRes = mvc.perform(get(BASE_EXP + "/{id}/summary", expenseId).with(asUser(memberId)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode summary = om.readTree(summaryRes.getResponse().getContentAsString());
        BigDecimal itemsTotal = new BigDecimal(summary.get("itemsTotal").asText()).setScale(2, HALF_UP);
        assertThat(itemsTotal).isEqualByComparingTo(total);
    }

    @Test
    @DisplayName("อัปเดต item (PUT /items/{id}) แบบ full field, ตรวจ amountThb เปลี่ยนตามและ round 2 ตำแหน่ง")
    void item_update_full() throws Exception {
        var res = mvc.perform(post(BASE_ITEMS, expenseId).with(asAdmin(adminId))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name","EUR Bread")
                        .param("amount","4.20")
                        .param("currency","EUR"))
                .andExpect(status().isCreated())
                .andReturn();
        long itemId = om.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        var upd = mvc.perform(put(BASE_ITEMS + "/{iid}", expenseId, itemId).with(asAdmin(adminId))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name","EUR Bread XL")
                        .param("amount","5.555")
                        .param("currency","EUR"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = om.readTree(upd.getResponse().getContentAsString());
        BigDecimal amtRounded = new BigDecimal("5.56"); // controller scale(2) ก่อนคูณ
        BigDecimal thb = amtRounded.multiply(new BigDecimal("39.90")).setScale(2, HALF_UP);
        assertThat(new BigDecimal(node.get("amountThb").asText())).isEqualByComparingTo(thb);
        assertThat(node.get("name").asText()).isEqualTo("EUR Bread XL");
    }

    @Test
    @DisplayName("ลบ item (DELETE /items/{id}) แล้วรวม total และ summary ลดลง")
    void item_delete_affects_totals() throws Exception {
        var r1 = mvc.perform(post(BASE_ITEMS, expenseId).with(asAdmin(adminId))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name","DelTHB")
                        .param("amount","10.00")
                        .param("currency","THB"))
                .andReturn();
        long id1 = om.readTree(r1.getResponse().getContentAsString()).get("id").asLong();

        var beforeTotal = mvc.perform(get(BASE_ITEMS + "/total", expenseId).with(asUser(memberId)))
                .andReturn();
        BigDecimal totalBefore = new BigDecimal(beforeTotal.getResponse().getContentAsString());

        mvc.perform(delete(BASE_ITEMS + "/{iid}", expenseId, id1).with(asAdmin(adminId)))
                .andExpect(status().isNoContent());

        var afterTotal = mvc.perform(get(BASE_ITEMS + "/total", expenseId).with(asUser(memberId)))
                .andReturn();
        BigDecimal totalAfter = new BigDecimal(afterTotal.getResponse().getContentAsString());
        assertThat(totalAfter).isLessThan(totalBefore);
    }

    @Test
    @DisplayName("สร้าง share: แบบเปอร์เซ็นต์กับแบบมูลค่า ตรวจค่า original และ THB (scale ก่อนคูณเรต)")
    void share_add_value_and_percent() throws Exception {
        var items = itemRepo.findByExpense_Id(expenseId);
        long jpyItemId = items.stream().filter(i -> "JPY".equals(i.getCurrency())).findFirst().orElseThrow().getId();
        long usdItemId = items.stream().filter(i -> "USD".equals(i.getCurrency())).findFirst().orElseThrow().getId();

        mvc.perform(post(BASE_SHARES, expenseId, jpyItemId).with(asAdmin(adminId))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("participantUserId", String.valueOf(memberId))
                        .param("sharePercent", "12.5"))
                .andExpect(status().isOk());
        var sJPY = shareRepo.findByExpenseItem_Id(jpyItemId).stream().findFirst().orElseThrow();
        assertThat(sJPY.getSharePercent()).isEqualByComparingTo("12.5");
        assertThat(sJPY.getShareOriginalValue()).isEqualByComparingTo("100.00"); // 800 * 12.5% = 100.00
        assertThat(sJPY.getShareValue()).isEqualByComparingTo(new BigDecimal("24.50")); // 100 * 0.245

        mvc.perform(post(BASE_SHARES, expenseId, usdItemId).with(asAdmin(adminId))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("participantUserId", String.valueOf(memberId))
                        .param("shareValue", "12.345"))
                .andExpect(status().isOk());
        var sUSD = shareRepo.findByExpenseItem_Id(usdItemId).stream()
                .filter(s -> s.getSharePercent() == null).findFirst().orElseThrow();
        assertThat(sUSD.getShareOriginalValue()).isEqualByComparingTo("12.35"); // scale ก่อนคูณ
        assertThat(sUSD.getShareValue()).isEqualByComparingTo(new BigDecimal("447.69")); // 12.35 * 36.25 = 447.6875 → 447.69
    }

    @Test
    @DisplayName("อัปเดต share: เปลี่ยนจาก value → percent และคำนวณ THB ใหม่ (ตามนโยบาย scale ก่อนคูณ)")
    void share_update_switch_mode() throws Exception {
        var items = itemRepo.findByExpense_Id(expenseId);
        long usdItemId = items.stream().filter(i -> "USD".equals(i.getCurrency())).findFirst().orElseThrow().getId();

        mvc.perform(post(BASE_SHARES, expenseId, usdItemId).with(asAdmin(adminId))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("participantUserId", String.valueOf(memberId))
                        .param("shareValue", "10.00"))
                .andExpect(status().isOk());
        var share = shareRepo.findByExpenseItem_Id(usdItemId).get(0);

        mvc.perform(put(BASE_SHARES + "/{sid}", expenseId, usdItemId, share.getId()).with(asAdmin(adminId))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("sharePercent", "50"))
                .andExpect(status().isOk());

        var updated = shareRepo.findById(share.getId()).orElseThrow();
        assertThat(updated.getSharePercent()).isEqualByComparingTo("50");
        var item = itemRepo.findById(usdItemId).orElseThrow();
        BigDecimal original = item.getAmount().multiply(new BigDecimal("0.50")).setScale(2, HALF_UP);
        BigDecimal thb = original.multiply(new BigDecimal("36.25")).setScale(2, HALF_UP);
        assertThat(updated.getShareOriginalValue()).isEqualByComparingTo(original);
        assertThat(updated.getShareValue()).isEqualByComparingTo(thb);
    }

    @Test
    @DisplayName("ลบ share: DELETE /shares/{id} และตรวจว่าไม่เหลือใน repo")
    void share_delete() throws Exception {
        var items = itemRepo.findByExpense_Id(expenseId);
        long thbItemId = items.stream().filter(i -> "THB".equals(i.getCurrency())).findFirst().orElseThrow().getId();

        mvc.perform(post(BASE_SHARES, expenseId, thbItemId).with(asAdmin(adminId))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("participantUserId", String.valueOf(memberId))
                        .param("shareValue", "15"))
                .andExpect(status().isOk());
        var s = shareRepo.findByExpenseItem_Id(thbItemId).get(0);

        mvc.perform(delete(BASE_SHARES + "/{sid}", expenseId, thbItemId, s.getId()).with(asAdmin(adminId)))
                .andExpect(status().isNoContent());

        assertThat(shareRepo.findById(s.getId())).isEmpty();
    }

    @Test
    @DisplayName("สิทธิ์: ผู้ใช้ที่ถูกเพิ่มเป็น item-share (แม้ไม่ใช่สมาชิกกลุ่ม) สามารถดู expense/items/shares ได้")
    void outsider_added_as_share_can_view() throws Exception {
        var items = itemRepo.findByExpense_Id(expenseId);
        long thbItemId = items.stream().filter(i -> "THB".equals(i.getCurrency())).findFirst().orElseThrow().getId();

        mvc.perform(post(BASE_SHARES, expenseId, thbItemId).with(asAdmin(adminId))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("participantUserId", String.valueOf(outsiderId))
                        .param("shareValue", "5"))
                .andExpect(status().isOk());

        mvc.perform(get(BASE_EXP + "/{id}", expenseId).with(asUser(outsiderId)))
                .andExpect(status().isOk());
        mvc.perform(get(BASE_ITEMS, expenseId).with(asUser(outsiderId)))
                .andExpect(status().isOk());
        mvc.perform(get(BASE_SHARES, expenseId, thbItemId).with(asUser(outsiderId)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /{id}/total-items → รวม THB จากหลายสกุลด้วยเรตที่ล็อกไว้ (round ต่อรายการ)")
    void items_total_multi_currency_locked_fx() throws Exception {
        var items = itemRepo.findByExpense_Id(expenseId);

        BigDecimal usd = items.stream().filter(i -> "USD".equals(i.getCurrency()))
                .map(ExpenseItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal jpy = items.stream().filter(i -> "JPY".equals(i.getCurrency()))
                .map(ExpenseItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal thb = items.stream().filter(i -> "THB".equals(i.getCurrency()) || i.getCurrency() == null)
                .map(ExpenseItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal usdTHB = usd.multiply(new BigDecimal("36.25")).setScale(2, HALF_UP);
        BigDecimal jpyTHB = jpy.multiply(new BigDecimal("0.245")).setScale(2, HALF_UP);
        BigDecimal expect = thb.setScale(2, HALF_UP).add(usdTHB).add(jpyTHB).setScale(2, HALF_UP);

        mvc.perform(get(BASE_EXP + "/{id}/total-items", expenseId).with(asUser(memberId)))
                .andExpect(status().isOk())
                .andExpect(content().string(expect.toPlainString()));
    }

    @Test
    @DisplayName("GET /{id}/total-items → เมื่อไม่มีรายการย่อยเป็น 0")
    void items_total_zero_when_no_items() throws Exception {
        var e2 = new Expense();
        e2.setGroup(groupRepo.getReferenceById(groupId));
        e2.setPayer(userRepo.getReferenceById(memberId));
        e2.setAmount(BigDecimal.ZERO);
        e2.setType(ExpenseType.EQUAL);
        e2.setTitle("Empty");
        e2.setStatus(ExpenseStatus.OPEN);
        e2.setExchangeRatesJson("{\"THB\":1,\"USD\":36.25}");
        long id2 = expenseRepo.save(e2).getId();

        mvc.perform(get(BASE_EXP + "/{id}/total-items", id2).with(asUser(memberId)))
                .andExpect(status().isOk())
                .andExpect(content().string("0"));
    }

    @Test
    @DisplayName("GET /{id}/total-items → 404 เมื่อไม่พบ")
    void items_total_not_found() throws Exception {
        mvc.perform(get(BASE_EXP + "/{id}/total-items", 999999L).with(asAdmin(adminId)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /{id}/total-verified → รวมเฉพาะ VERIFIED")
    void verified_total_sum_verified_only() throws Exception {
        String expected = paymentRepo == null
                ? "0"
                : paymentRepo.findByExpense_Id(expenseId).stream()
                .filter(p -> p.getStatus() == PaymentStatus.VERIFIED)
                .map(ExpensePayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, HALF_UP).toPlainString();

        mvc.perform(get(BASE_EXP + "/{id}/total-verified", expenseId).with(asUser(memberId)))
                .andExpect(status().isOk())
                .andExpect(content().string(expected));
    }

    @Test
    @DisplayName("GET /{id}/summary → itemsTotal(THB ตามเรตล็อก, round ต่อรายการ) และ verifiedTotal")
    void summary_totals() throws Exception {
        var items = itemRepo.findByExpense_Id(expenseId);

        BigDecimal total = BigDecimal.ZERO;
        for (var it : items) {
            BigDecimal amt = it.getAmount();
            String ccy = it.getCurrency();
            BigDecimal thb;
            if ("USD".equals(ccy)) thb = amt.multiply(new BigDecimal("36.25")).setScale(2, HALF_UP);
            else if ("JPY".equals(ccy)) thb = amt.multiply(new BigDecimal("0.245")).setScale(2, HALF_UP);
            else if ("EUR".equals(ccy)) thb = amt.multiply(new BigDecimal("39.90")).setScale(2, HALF_UP);
            else if ("GBP".equals(ccy)) thb = amt.multiply(new BigDecimal("46.50")).setScale(2, HALF_UP);
            else thb = amt.setScale(2, HALF_UP);
            total = total.add(thb);
        }
        total = total.setScale(2, HALF_UP);

        BigDecimal verified = paymentRepo == null
                ? BigDecimal.ZERO
                : paymentRepo.findByExpense_Id(expenseId).stream()
                .filter(p -> p.getStatus() == PaymentStatus.VERIFIED)
                .map(ExpensePayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, HALF_UP);

        mvc.perform(get(BASE_EXP + "/{id}/summary", expenseId).with(asUser(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemsTotal").value(total.doubleValue()))
                .andExpect(jsonPath("$.verifiedTotal").value(verified.doubleValue()));
    }

    @Test
    @DisplayName("ชุด ISP: boundary amounts ในการสร้าง expense 0, 0.01, 9999999999.99, 123.456 (accept)")
    void create_amount_boundary_isp() throws Exception {
        String z = """
                {"groupId":%d,"payerUserId":%d,"amount":0,"type":"EQUAL","title":"Z","status":"OPEN"}
                """.formatted(groupId, memberId);
        mvc.perform(post(BASE_EXP).with(asUser(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(z.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(0.0));

        String cent = """
                {"groupId":%d,"payerUserId":%d,"amount":0.01,"type":"EQUAL","title":"C","status":"OPEN"}
                """.formatted(groupId, memberId);
        mvc.perform(post(BASE_EXP).with(asUser(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cent.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(0.01));

        String big = """
                {"groupId":%d,"payerUserId":%d,"amount":9999999999.99,"type":"EQUAL","title":"B","status":"OPEN"}
                """.formatted(groupId, memberId);
        mvc.perform(post(BASE_EXP).with(asUser(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(big.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(9999999999.99));

        String threeDp = """
                {"groupId":%d,"payerUserId":%d,"amount":123.456,"type":"EQUAL","title":"D","status":"OPEN"}
                """.formatted(groupId, memberId);
        mvc.perform(post(BASE_EXP).with(asUser(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(threeDp.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("สิทธิ์: USER ที่ไม่ใช่สมาชิกกลุ่มสร้าง expense ในกลุ่ม → 403")
    void create_forbidden_for_outsider() throws Exception {
        String body = """
                {"groupId":%d,"payerUserId":%d,"amount":10,"type":"EQUAL","title":"Nope","status":"OPEN"}
                """.formatted(groupId, outsiderId);
        mvc.perform(post(BASE_EXP).with(asUser(outsiderId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("อัปเดตเฉพาะบางฟิลด์ของ expense: เปลี่ยน title อย่างเดียว amount คงเดิม")
    void update_partial_patch_like() throws Exception {
        var before = expenseRepo.findById(expenseId).orElseThrow();
        var oldAmount = before.getAmount();

        String body = """
                {"title":"Only Title Changed"}
                """;
        mvc.perform(put(BASE_EXP + "/{id}", expenseId).with(asAdmin(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Only Title Changed"));

        var after = expenseRepo.findById(expenseId).orElseThrow();
        assertThat(after.getAmount()).isEqualByComparingTo(oldAmount);
    }
}
