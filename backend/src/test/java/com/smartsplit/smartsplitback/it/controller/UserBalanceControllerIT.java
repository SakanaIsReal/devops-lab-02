package com.smartsplit.smartsplitback.it.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartsplit.smartsplitback.it.security.BaseIntegrationTest;
import com.smartsplit.smartsplitback.repository.BalanceQueryRepository;
import com.smartsplit.smartsplitback.repository.BalanceRowProjection;
import com.smartsplit.smartsplitback.security.JwtService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


class UserBalanceControllerIT extends BaseIntegrationTest {

    private static final String BASE = "/api/me/balances";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JwtService jwtService;

    @MockitoBean
    private BalanceQueryRepository balanceRepo;

    
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

    // ---------- Helper: สร้าง Projection จำลอง ----------
    private static BalanceRowProjection row(
            String direction, long counterpartyUserId, String counterpartyUserName, String counterpartyAvatarUrl,
            Long groupId, String groupName, Long expenseId, String expenseTitle, BigDecimal remaining
    ) {
        return new BalanceRowProjection() {
            public String getDirection()            { return direction; }
            public Long   getCounterpartyUserId()   { return counterpartyUserId; }
            public String getCounterpartyUserName() { return counterpartyUserName; }
            public String getCounterpartyAvatarUrl(){ return counterpartyAvatarUrl; }
            public Long   getGroupId()              { return groupId; }
            public String getGroupName()            { return groupName; }
            public Long   getExpenseId()            { return expenseId; }
            public String getExpenseTitle()         { return expenseTitle; }
            public BigDecimal getRemaining()        { return remaining; }
        };
    }

    // ---------- TESTS ----------

    @Test
    @DisplayName("GET /api/me/balances → ต้องล็อกอิน: ไม่มี token = 401")
    void list_requires_auth() throws Exception {
        mvc.perform(get(BASE))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(balanceRepo);
    }

    @Test
    @DisplayName("GET /api/me/balances → คืนรายการ balance ที่ map จาก projection เป็น DTO (ผ่าน Security จริง)")
    void list_balances_ok() throws Exception {
        long me = 101L;

        // จัดข้อมูลจำลองจากชั้น query (YOU_OWE/OWES_YOU ปนกัน)
        var r1 = row("YOU_OWE",  202L, "Alice",  "alice.png",  10L, "Trip",  1001L, "Hotel",   new BigDecimal("123.45"));
        var r2 = row("OWES_YOU", 303L, "Bob",    "bob.png",    10L, "Trip",  1002L, "Food",    new BigDecimal("50.00"));
        var r3 = row("YOU_OWE",  404L, "Carol",  "carol.png",  11L, "Party", 1003L, "Drink",   new BigDecimal("10.55"));

        when(balanceRepo.findBalancesForUser(eq(me))).thenReturn(List.of(r1, r2, r3));

        mvc.perform(get(BASE).with(asUser(me)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                // ตรวจจำนวนรายการ
                .andExpect(jsonPath("$", Matchers.hasSize(3)))
                // ตรวจบาง field สำคัญของแถวแรก (mapping ถูกต้อง)
                .andExpect(jsonPath("$[0].direction").value("YOU_OWE"))
                .andExpect(jsonPath("$[0].counterpartyUserId").value(202))
                .andExpect(jsonPath("$[0].counterpartyUserName").value("Alice"))
                .andExpect(jsonPath("$[0].counterpartyAvatarUrl").value("alice.png"))
                .andExpect(jsonPath("$[0].groupId").value(10))
                .andExpect(jsonPath("$[0].groupName").value("Trip"))
                .andExpect(jsonPath("$[0].expenseId").value(1001))
                .andExpect(jsonPath("$[0].expenseTitle").value("Hotel"))
                .andExpect(jsonPath("$[0].remaining").value(123.45))
                // ตรวจแถวที่สอง direction ตรง
                .andExpect(jsonPath("$[1].direction").value("OWES_YOU"));

        // ยืนยันว่าชั้น query ถูกเรียกด้วย userId ปัจจุบันจาก token จริง ๆ
        verify(balanceRepo, times(1)).findBalancesForUser(eq(me));
        verifyNoMoreInteractions(balanceRepo);
    }

    @Test
    @DisplayName("GET /api/me/balances/summary → รวมยอด youOwe / youAreOwed ถูกต้อง (ผ่าน Security จริง)")
    void summary_ok() throws Exception {
        long me = 202L;

        var r1 = row("YOU_OWE",  999L, "X", "x.png", 1L, "G1", 11L, "E1", new BigDecimal("10.00"));
        var r2 = row("OWES_YOU", 888L, "Y", "y.png", 1L, "G1", 12L, "E2", new BigDecimal("20.25"));
        var r3 = row("YOU_OWE",  777L, "Z", "z.png", 2L, "G2", 13L, "E3", new BigDecimal("5.75"));
        // direction อื่น (ถ้ามี) ควรถูกมองข้ามใน summary
        var r4 = row("NEUTRAL",  666L, "W", "w.png", 2L, "G2", 14L, "E4", new BigDecimal("999.99"));

        when(balanceRepo.findBalancesForUser(eq(me))).thenReturn(List.of(r1, r2, r3, r4));

        mvc.perform(get(BASE + "/summary").with(asUser(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.youOweTotal").value(15.75))     // 10.00 + 5.75
                .andExpect(jsonPath("$.youAreOwedTotal").value(20.25)) // 20.25

        ;

        verify(balanceRepo, times(1)).findBalancesForUser(eq(me));
        verifyNoMoreInteractions(balanceRepo);
    }

    @Test
    @DisplayName("GET /api/me/balances/summary → ไม่มี token = 401")
    void summary_requires_auth() throws Exception {
        mvc.perform(get(BASE + "/summary"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(balanceRepo);
    }
}
