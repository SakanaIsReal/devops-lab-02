package com.smartsplit.smartsplitback.it.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartsplit.smartsplitback.it.security.BaseIntegrationTest;
import com.smartsplit.smartsplitback.model.User;
import com.smartsplit.smartsplitback.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("AuthController IT (end-to-end to DB)")
class AuthControllerIT extends BaseIntegrationTest {

    private static final String BASE = "/api/auth";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired UserRepository users;

    @BeforeEach
    void setUp() {
        users.deleteAll();
    }

    // ---------- helpers ----------
    private String registerJson(String email, String userName, String password) {
        return """
            {
              "email": %s,
              "userName": %s,
              "password": %s
            }
            """.formatted(
                om.valueToTree(email).toString(),
                om.valueToTree(userName).toString(),
                om.valueToTree(password).toString()
        );
    }

    private String loginJson(String email, String password) {
        return """
            {
              "email": %s,
              "password": %s
            }
            """.formatted(
                om.valueToTree(email).toString(),
                om.valueToTree(password).toString()
        );
    }

    private String extractTokenKey(Map<String, Object> body) {
        for (String k : new String[]{"token", "accessToken", "jwt", "access_token"}) {
            if (body.containsKey(k) && body.get(k) instanceof String s && !s.isBlank()) {
                return k;
            }
        }
        return null;
    }

    // ---------- TESTS ----------

    @Test
    @DisplayName("POST /auth/register → 201 และบันทึกผู้ใช้ลง DB")
    void register_created_and_persisted() throws Exception {
        var email = "newuser@example.com";
        var reqJson = registerJson(email, "newbie", "P@ssw0rd!");

        var mvcResult = mvc.perform(post(BASE + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqJson))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                // มีบางฟิลด์ token ใด ๆ กลับมา (ชื่ออาจต่าง จึงเช็คผ่าน body ทีหลัง)
                .andExpect(content().string(not(isEmptyOrNullString())))
                .andReturn();

        // ตรวจ DB
        Optional<User> saved = users.findByEmail(email);
        assertThat(saved).isPresent();
        assertThat(saved.get().getUserName()).isEqualTo("newbie");
        assertThat(saved.get().getPasswordHash()).isNotBlank();

        // ตรวจ response มี token key ใด ๆ
        var body = om.readValue(mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8),
                new TypeReference<Map<String, Object>>() {});
        var tokenKey = extractTokenKey(body);
        assertThat(tokenKey)
                .withFailMessage("AuthResponse ควรจะมี token เช่น token/accessToken/jwt/access_token")
                .isNotNull();
    }

    @Test
    @DisplayName("POST /auth/register (อีเมลซ้ำ) → 4xx")
    void register_duplicate_email_should_fail() throws Exception {

        mvc.perform(post(BASE + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("dup@example.com", "u1", "x123456")))
                .andExpect(status().isCreated());

        // สมัครซ้ำอีเมลเดิม → 4xx (ขึ้นกับ Service จะเป็น 400/409)
        mvc.perform(post(BASE + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("dup@example.com", "u2", "y123456")))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("POST /auth/login → 200 เมื่อล็อกอินด้วยบัญชีที่เพิ่งสมัคร และได้ token")
    void login_ok_after_register() throws Exception {
        // สมัครก่อน
        mvc.perform(post(BASE + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("loginok@example.com", "logok", "StrongP@ss1")))
                .andExpect(status().isCreated());

        // ล็อกอิน
        var result = mvc.perform(post(BASE + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("loginok@example.com", "StrongP@ss1")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        var body = om.readValue(result.getResponse().getContentAsString(StandardCharsets.UTF_8),
                new TypeReference<Map<String, Object>>() {});
        var tokenKey = extractTokenKey(body);
        assertThat(tokenKey).isNotNull();
        assertThat((String) body.get(tokenKey)).isNotBlank();
    }

    @Test
    @DisplayName("POST /auth/login → 401 เมื่อรหัสผ่านผิด")
    void login_wrong_password_unauthorized() throws Exception {

        mvc.perform(post(BASE + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("wrongpass@example.com", "wp", "Correct123")))
                .andExpect(status().isCreated());

        mvc.perform(post(BASE + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("wrongpass@example.com", "incorrect")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /auth/login → 401 เมื่อไม่พบผู้ใช้")
    void login_user_not_found_unauthorized() throws Exception {
        mvc.perform(post(BASE + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("noone@example.com", "whatever")))
                .andExpect(status().isUnauthorized());
    }
}
