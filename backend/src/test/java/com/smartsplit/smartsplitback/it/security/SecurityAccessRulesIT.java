package com.smartsplit.smartsplitback.it.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.anything;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// ทดสอบกฎจาก SecurityConfig: permitAll บางเส้นทาง, anyRequest().authenticated()
class SecurityAccessRulesIT extends BaseIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("permitAll: /actuator/health ไม่ต้อง auth ควรไม่เป็น 401/403")
    void actuator_health_permit_all() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").exists()); // โครงสร้างอาจต่างตาม actuator config
    }

    @Test
    @DisplayName("permitAll: GET /files/** ไม่ต้อง auth (ถ้าไฟล์ไม่พบ ก็อย่างน้อยไม่ใช่ 401/403)")
    void files_get_permit_all() throws Exception {
        mvc.perform(get("/files/__not_found__.png"))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    if (s == 401 || s == 403) {
                        throw new AssertionError("Expected permitAll, but got status " + s);
                    }
                });
    }

    @Test
    @DisplayName("authenticated: เส้นทางอื่น ๆ ที่ไม่ permitAll → ไม่มี header ต้อง 401")
    void any_other_authenticated_requires_token() throws Exception {
        mvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }
}
