package com.smartsplit.smartsplitback.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("SecurityConfig: integration rules")
class SecurityConfigIT {

    @Autowired MockMvc mockMvc;

   
    private void expectNotAuthzBlocked(ResultActions ra) throws Exception {
        ra.andExpect(result -> {
            int s = result.getResponse().getStatus();
            assertThat(s).as("status should not be 401 or 403").isNotIn(401, 403);
        });
    }

    // ---------- PermitAll rules ----------
    @Nested
    @DisplayName("permitAll routes")
    class PermitAll {

        @Test
        @DisplayName("GET /files/** → ผ่าน security (ไม่มีไฟล์/handler → 404)")
        void files_permitAll() throws Exception {
            mockMvc.perform(get("/files/some-folder/some.png"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("/api/auth/** → ผ่าน security (ใช้ __probe เพื่อหลบ logic คอนโทรลเลอร์ → 404)")
        void auth_permitAll_probe() throws Exception {
            mockMvc.perform(get("/api/auth/__probe"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("/swagger-ui/index.html → มีรีซอร์สจริง → 200 OK")
        void swagger_permitAll_ok() throws Exception {
            mockMvc.perform(get("/swagger-ui/index.html"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("/v3/api-docs → ปล่อยผ่าน security (สถานะขึ้นกับสภาพแอป แต่ต้องไม่ใช่ 401/403)")
        void v3_api_docs_permitAll_notBlocked() throws Exception {
            expectNotAuthzBlocked(mockMvc.perform(get("/v3/api-docs")));
        }

        @Test
        @DisplayName("/error → ปล่อยผ่าน security (Spring error handler) → ไม่ใช่ 401/403")
        void error_permitAll_notBlocked() throws Exception {
            expectNotAuthzBlocked(mockMvc.perform(get("/error")));
        }
    }

    // ---------- Authenticated (anyRequest) ----------
    @Nested
    @DisplayName("authenticated routes (anyRequest)")
    class Authenticated {

        @Test
        @DisplayName("ไม่ล็อกอิน → 401 Unauthorized")
        void protected_without_login_401() throws Exception {
            mockMvc.perform(get("/authz/probe"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(status().reason("Unauthorized"));
        }

        @Test
        @WithMockUser // USER by default
        @DisplayName("ล็อกอินแล้ว → ผ่าน security (ไม่มี handler → 404)")
        void protected_with_login_404() throws Exception {
            mockMvc.perform(get("/authz/probe"))
                    .andExpect(status().isNotFound());
        }
    }

    // ---------- Admin-only ----------
    @Nested
    @DisplayName("/api/admin/**")
    class AdminOnly {

        @Test
        @DisplayName("ไม่ล็อกอิน → 401 Unauthorized")
        void admin_without_login_401() throws Exception {
            mockMvc.perform(get("/api/admin/panel"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(status().reason("Unauthorized"));
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("ล็อกอินด้วย USER → 403 Forbidden")
        void admin_as_user_403() throws Exception {
            mockMvc.perform(get("/api/admin/panel"))
                    .andExpect(status().isForbidden())
                    .andExpect(status().reason("Forbidden"));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ล็อกอินด้วย ADMIN → ผ่าน security (ไม่มี handler → 404)")
        void admin_as_admin_404() throws Exception {
            mockMvc.perform(get("/api/admin/panel"))
                    .andExpect(status().isNotFound());
        }
    }

    // ---------- Method & CSRF ----------
    @Nested
    @DisplayName("POST/PUT กับเส้นทางที่ต้อง auth (CSRF disabled)")
    class MethodsAndCsrf {

        @Test
        @DisplayName("POST โดยไม่ล็อกอิน → 401 (ไม่ใช่ 403 CSRF)")
        void post_protected_without_login_401() throws Exception {
            mockMvc.perform(post("/authz/probe"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(status().reason("Unauthorized"));
        }

        @Test
        @WithMockUser
        @DisplayName("POST เมื่อล็อกอินแล้ว → ผ่าน security (ไม่มี handler → 404) ไม่ต้องส่ง CSRF")
        void post_protected_with_login_404() throws Exception {
            mockMvc.perform(post("/authz/probe")
                            .contentType("application/json")
                            .content("{}"))
                    .andExpect(status().isNotFound());
        }
    }

    // ---------- Sanity: Only GET /files/** is permitAll ----------
    @Nested
    @DisplayName("เฉพาะ GET /files/** ที่ permitAll")
    class FilesOnlyGet {

        @Test
        @DisplayName("POST /files/** โดยไม่ล็อกอิน → 401")
        void post_files_requires_auth() throws Exception {
            mockMvc.perform(post("/files/anything"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(status().reason("Unauthorized"));
        }

        @Test
        @WithMockUser
        @DisplayName("POST /files/** เมื่อล็อกอิน → ผ่าน security (ไม่มี handler → 404)")
        void post_files_with_login_404() throws Exception {
            mockMvc.perform(post("/files/anything"))
                    .andExpect(status().isNotFound());
        }
    }
}
