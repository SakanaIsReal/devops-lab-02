package com.smartsplit.smartsplitback.it.security;

import com.smartsplit.smartsplitback.model.Role;
import com.smartsplit.smartsplitback.model.User;
import com.smartsplit.smartsplitback.repository.UserRepository;
import com.smartsplit.smartsplitback.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("JWT Authentication via JwtAuthFilter (with @MockBean JwtService)")
class SecurityJwtAuthIT extends BaseIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;

    @MockitoBean JwtService jwtService;

    long adminId;
    long userId;

    @BeforeEach
    void init() {
        users.deleteAll();

        var admin = new User();
        admin.setEmail("admin@example.com");
        admin.setUserName("Admin");
        admin.setPasswordHash("{noop}x");
        admin.setRole(Role.ADMIN);
        adminId = users.save(admin).getId();

        var user = new User();
        user.setEmail("me@example.com");
        user.setUserName("Me");
        user.setPasswordHash("{noop}x");
        user.setRole(Role.USER);
        userId = users.save(user).getId();
    }

    @Test
    @DisplayName("401: ไม่มี Authorization header")
    void no_header_unauthorized() throws Exception {
        mvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("401: token ไม่ถูกต้อง → JwtAuthFilter ไม่ยอมรับ")
    void bad_token_unauthorized() throws Exception {
        when(jwtService.getUserId("bad")).thenReturn(null);
        when(jwtService.getSubject("bad")).thenReturn(null);

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer bad"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("200: USER token ถูกต้อง → เข้าถึง /api/users/me ได้")
    void user_token_ok_get_me() throws Exception {
        // จำลอง token "u" คืนค่า uid และ role เป็น USER
        when(jwtService.getUserId("u")).thenReturn(userId);
        when(jwtService.getRoleCode("u")).thenReturn(1); // สมมติ 1 = USER ตามระบบคุณ
        // ไม่จำเป็นต้อง stub getSubject เมื่อ getUserId != null

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer u"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.email").value("me@example.com"));
    }

    @Test
    @DisplayName("403: USER token → เรียก endpoint ที่ต้อง ADMIN (เช่น GET /api/users)")
    void user_forbidden_on_admin_endpoint() throws Exception {
        when(jwtService.getUserId("u")).thenReturn(userId);
        when(jwtService.getRoleCode("u")).thenReturn(1); // USER

        mvc.perform(get("/api/users").header("Authorization", "Bearer u"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("200: ADMIN token → เรียก endpoint ADMIN-only ผ่าน")
    void admin_token_ok_on_admin_endpoint() throws Exception {
        when(jwtService.getUserId("a")).thenReturn(adminId);
        when(jwtService.getRoleCode("a")).thenReturn(JwtService.ROLE_ADMIN); // ใช้ค่าคงที่จากคลาสจริง

        mvc.perform(get("/api/users").header("Authorization", "Bearer a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists());
    }

    @Test
    @DisplayName("200: เคส legacy — getUserId(null) แต่ subject เป็นตัวเลข → ยังล็อกอินได้")
    void legacy_subject_numeric() throws Exception {
        when(jwtService.getUserId("legacy")).thenReturn(null);
        when(jwtService.getSubject("legacy")).thenReturn(String.valueOf(userId));
        when(jwtService.getRoleCode("legacy")).thenReturn(1); // USER

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer legacy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId));
    }
}
