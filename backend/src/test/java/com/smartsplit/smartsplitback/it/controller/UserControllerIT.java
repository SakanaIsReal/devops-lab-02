package com.smartsplit.smartsplitback.it.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartsplit.smartsplitback.it.security.BaseIntegrationTest;
import com.smartsplit.smartsplitback.model.Role;
import com.smartsplit.smartsplitback.model.User;
import com.smartsplit.smartsplitback.repository.UserRepository;
import com.smartsplit.smartsplitback.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserControllerIT extends BaseIntegrationTest {

    private static final String BASE = "/api/users";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired UserRepository users;
    @Autowired JwtService jwtService;

    long adminId;
    long userId;
    long otherId;

    private String jwtFor(long uid, int roleCode) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtService.CLAIM_UID, uid);
        claims.put(JwtService.CLAIM_ROLE, roleCode);
        String subject = "uid:" + uid;
        return jwtService.generate(subject, claims, 3600);
    }

    private RequestPostProcessor asAdmin(long id) {
        return req -> { req.addHeader("Authorization",
                "Bearer " + jwtFor(id, JwtService.ROLE_ADMIN)); return req; };
    }

    private RequestPostProcessor asUser(long id) {
        return req -> { req.addHeader("Authorization",
                "Bearer " + jwtFor(id, JwtService.ROLE_USER)); return req; };
    }

    @BeforeEach
    void setUp() {
        users.deleteAll();

        var admin = new User();
        admin.setEmail("admin@example.com");
        admin.setUserName("Admin");
        admin.setFirstName("Alice");
        admin.setLastName("Adminson");
        admin.setPhone("090-111-1111");
        admin.setAvatarUrl("http://seed/av-admin.png");
        admin.setQrCodeUrl("http://seed/qr-admin.png");
        admin.setPasswordHash("{noop}x");
        admin.setRole(Role.ADMIN);
        adminId = users.save(admin).getId();

        var u = new User();
        u.setEmail("me@example.com");
        u.setUserName("Me");
        u.setFirstName("Maya");
        u.setLastName("Meow");
        u.setPhone("090-222-2222");
        u.setAvatarUrl("http://seed/av-me.png");
        u.setQrCodeUrl("http://seed/qr-me.png");
        u.setPasswordHash("{noop}x");
        u.setRole(Role.USER);
        userId = users.save(u).getId();

        var o = new User();
        o.setEmail("other@example.com");
        o.setUserName("Other");
        o.setFirstName("Oscar");
        o.setLastName("Otherly");
        o.setPhone("090-333-3333");
        o.setAvatarUrl("http://seed/av-other.png");
        o.setQrCodeUrl("http://seed/qr-other.png");
        o.setPasswordHash("{noop}x");
        o.setRole(Role.USER);
        otherId = users.save(o).getId();
    }

    // ---------- READ ----------

    @Test @DisplayName("GET /api/users (ADMIN เท่านั้น) + ได้ฟิลด์ครบ")
    void list_users_admin_only() throws Exception {
        mvc.perform(get(BASE).with(asAdmin(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].email").exists())
                .andExpect(jsonPath("$[0].userName").exists())
                .andExpect(jsonPath("$[0].phone").exists())
                .andExpect(jsonPath("$[0].avatarUrl").exists())
                .andExpect(jsonPath("$[0].qrCodeUrl").exists())
                .andExpect(jsonPath("$[0].firstName").exists())
                .andExpect(jsonPath("$[0].lastName").exists())
                .andExpect(jsonPath("$[0].roleCode").exists());

        mvc.perform(get(BASE).with(asUser(userId)))
                .andExpect(status().isForbidden());
    }

    @Test @DisplayName("GET /api/users/{id} เคารพ perm.canViewUser + ได้ฟิลด์ครบ")
    void get_user_by_id_with_perms() throws Exception {
        // self
        mvc.perform(get(BASE + "/" + userId).with(asUser(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.email").value("me@example.com"))
                .andExpect(jsonPath("$.firstName").value("Maya"))
                .andExpect(jsonPath("$.lastName").value("Meow"))
                .andExpect(jsonPath("$.roleCode").value(Role.USER.code()));

        // someone else (non-admin)
        mvc.perform(get(BASE + "/" + userId).with(asUser(otherId)))
                .andExpect(status().isForbidden());

        // admin
        mvc.perform(get(BASE + "/" + userId).with(asAdmin(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId));
    }

    @Test @DisplayName("GET /api/users/me ต้อง auth และหา user เจอ + ฟิลด์ครบ")
    void get_me() throws Exception {
        mvc.perform(get(BASE + "/me").with(asUser(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@example.com"))
                .andExpect(jsonPath("$.firstName").value("Maya"))
                .andExpect(jsonPath("$.lastName").value("Meow"))
                .andExpect(jsonPath("$.roleCode").value(Role.USER.code()));

        mvc.perform(get(BASE + "/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test @DisplayName("GET /api/users/search?q=... ส่ง Public-like fields (ไม่มี qrCodeUrl/firstName/lastName/roleCode)")
    void search_users() throws Exception {
        mvc.perform(get(BASE + "/search").param("q", "e").with(asUser(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").exists())
                .andExpect(jsonPath("$[0].userName").exists())
                .andExpect(jsonPath("$[0].phone").exists())
                .andExpect(jsonPath("$[0].avatarUrl").exists())
                .andExpect(jsonPath("$[0].qrCodeUrl").doesNotExist())
                .andExpect(jsonPath("$[0].firstName").doesNotExist())
                .andExpect(jsonPath("$[0].lastName").doesNotExist())
                .andExpect(jsonPath("$[0].roleCode").doesNotExist());
    }

    // ---------- CREATE ----------

    @Test
    @DisplayName("POST (JSON) /api/users -> USER ห้าม, ADMIN สำเร็จเมื่อระบุ password และกำหนด role/ชื่อ/รูป ได้")
    void create_user_json_permissions_and_success() throws Exception {
        String reqMissingPassword = """
            {"email":"newuser@example.com","userName":"newbie","phone":"090-000-0000","firstName":"New","lastName":"User","avatarUrl":"http://cli/av.png","qrCodeUrl":"http://cli/qr.png","role":1}
            """;

        mvc.perform(post(BASE)
                        .with(asAdmin(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqMissingPassword))
                .andExpect(status().isBadRequest());

        mvc.perform(post(BASE)
                        .with(asUser(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqMissingPassword))
                .andExpect(status().isForbidden());

        String reqOk = """
            {"email":"newuser@example.com","userName":"newbie","phone":"090-000-0000","firstName":"New","lastName":"User","avatarUrl":"http://cli/av.png","qrCodeUrl":"http://cli/qr.png","password":"pass123","role":1}
            """;

        mvc.perform(post(BASE)
                        .with(asAdmin(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqOk))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.email").value("newuser@example.com"))
                .andExpect(jsonPath("$.userName").value("newbie"))
                .andExpect(jsonPath("$.phone").value("090-000-0000"))
                .andExpect(jsonPath("$.firstName").value("New"))
                .andExpect(jsonPath("$.lastName").value("User"))
                .andExpect(jsonPath("$.avatarUrl").value("http://cli/av.png"))
                .andExpect(jsonPath("$.qrCodeUrl").value("http://cli/qr.png"))
                .andExpect(jsonPath("$.roleCode").value(Role.USER.code()));
    }

    @Test
    @DisplayName("POST (multipart) /api/users -> USER ห้าม, ADMIN สำเร็จเมื่อระบุ password และไฟล์อัปโหลด")
    void create_user_multipart_permissions_and_success() throws Exception {
        byte[] userJsonMissingPwd = """
            {"email":"imguser@example.com","userName":"img","firstName":"Img","lastName":"User","role":1}
            """.getBytes(StandardCharsets.UTF_8);

        var userPartMissingPwd = new MockMultipartFile(
                "user", "user.json", MediaType.APPLICATION_JSON_VALUE, userJsonMissingPwd);

        mvc.perform(multipart(BASE)
                        .file(userPartMissingPwd)
                        .with(asAdmin(adminId)))
                .andExpect(status().isBadRequest());

        mvc.perform(multipart(BASE)
                        .file(userPartMissingPwd)
                        .with(asUser(userId)))
                .andExpect(status().isForbidden());

        byte[] userJsonOk = """
            {"email":"imguser@example.com","userName":"img","firstName":"Img","lastName":"User","password":"pass123","role":1}
            """.getBytes(StandardCharsets.UTF_8);
        var userPartOk = new MockMultipartFile(
                "user", "user.json", MediaType.APPLICATION_JSON_VALUE, userJsonOk);
        var avatar = new MockMultipartFile(
                "avatar", "a.png", MediaType.IMAGE_PNG_VALUE, new byte[]{1,2,3});
        var qrCode = new MockMultipartFile(
                "qrCode", "q.png", MediaType.IMAGE_PNG_VALUE, new byte[]{4,5,6});

        mvc.perform(multipart(BASE)
                        .file(userPartOk).file(avatar).file(qrCode)
                        .with(asAdmin(adminId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.email").value("imguser@example.com"))
                .andExpect(jsonPath("$.userName").value("img"))
                .andExpect(jsonPath("$.firstName").value("Img"))
                .andExpect(jsonPath("$.lastName").value("User"))
                .andExpect(jsonPath("$.avatarUrl").exists())
                .andExpect(jsonPath("$.qrCodeUrl").exists())
                .andExpect(jsonPath("$.roleCode").value(Role.USER.code()));
    }

    // ---------- UPDATE (JSON) ----------

    @Test
    @DisplayName("PUT (JSON) /api/users/{id} -> ADMIN หรือเจ้าตัวเองเท่านั้น และอัปเดตฟิลด์ครบ")
    void update_user_json_permissions() throws Exception {
        var req = """
            {"email":"me2@example.com","userName":"Me-Updated","phone":"099-999-9999","firstName":"Maya2","lastName":"Meow2","avatarUrl":"http://cli/av2.png","qrCodeUrl":"http://cli/qr2.png"}
            """;

        // self update
        mvc.perform(put(BASE + "/" + userId)
                        .with(asUser(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me2@example.com"))
                .andExpect(jsonPath("$.userName").value("Me-Updated"))
                .andExpect(jsonPath("$.phone").value("099-999-9999"))
                .andExpect(jsonPath("$.firstName").value("Maya2"))
                .andExpect(jsonPath("$.lastName").value("Meow2"))
                .andExpect(jsonPath("$.avatarUrl").value("http://cli/av2.png"))
                .andExpect(jsonPath("$.qrCodeUrl").value("http://cli/qr2.png"));

        // non-owner, non-admin
        mvc.perform(put(BASE + "/" + userId)
                        .with(asUser(otherId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT (JSON) /api/users/{id} -> เปลี่ยน role: non-admin=403, admin เปลี่ยนตัวเอง=403, admin เปลี่ยนคนอื่น=200")
    void update_user_json_change_role_rules() throws Exception {
        // non-admin พยายามเปลี่ยน role -> 403
        var reqRoleAdmin = """
            {"roleCode":0}
            """;
        mvc.perform(put(BASE + "/" + userId)
                        .with(asUser(userId)) // self but not admin
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqRoleAdmin))
                .andExpect(status().isForbidden());

        // admin เปลี่ยน role ของตัวเอง -> 403
        mvc.perform(put(BASE + "/" + adminId)
                        .with(asAdmin(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqRoleAdmin))
                .andExpect(status().isForbidden());

        // admin เปลี่ยน role ของคนอื่น -> 200 และ roleCode = ADMIN
        mvc.perform(put(BASE + "/" + userId)
                        .with(asAdmin(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqRoleAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleCode").value(Role.ADMIN.code()));

        // ยืนยันที่ DB ว่าเปลี่ยนจริง (อ่านตรง ๆ)
        var updated = users.findById(userId).orElseThrow();
        assertThat(updated.getRole()).isEqualTo(Role.ADMIN);
    }

    // ---------- UPDATE (multipart) ----------

    @Test
    @DisplayName("PUT (multipart) /api/users/{id} -> เปลี่ยนรูปแล้ว avatarUrl/qrCodeUrl ใหม่ต้องถูกอัปเดต + อัปเดตฟิลด์อื่น")
    void update_user_multipart_files() throws Exception {
        var json = """
            {"email":"me3@example.com","userName":"with-image","phone":"088-888-8888","firstName":"Maya3","lastName":"Meow3"}
            """.getBytes(StandardCharsets.UTF_8);

        var userPart = new MockMultipartFile(
                "user", "user.json", MediaType.APPLICATION_JSON_VALUE, json);

        var avatar = new MockMultipartFile(
                "avatar", "a2.png", MediaType.IMAGE_PNG_VALUE, new byte[]{9,9,9});
        var qr     = new MockMultipartFile(
                "qr", "q2.png", MediaType.IMAGE_PNG_VALUE, new byte[]{8,8,8});

        mvc.perform(multipart(BASE + "/" + userId)
                        .file(userPart).file(avatar).file(qr)
                        .with(req -> { req.setMethod("PUT"); return req; })
                        .with(asUser(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me3@example.com"))
                .andExpect(jsonPath("$.userName").value("with-image"))
                .andExpect(jsonPath("$.phone").value("088-888-8888"))
                .andExpect(jsonPath("$.firstName").value("Maya3"))
                .andExpect(jsonPath("$.lastName").value("Meow3"))
                .andExpect(jsonPath("$.avatarUrl").exists())
                .andExpect(jsonPath("$.qrCodeUrl").exists());
    }

    // ---------- DELETE ----------

    @Test
    @DisplayName("DELETE /api/users/{id} -> ADMIN หรือเจ้าตัวเองลบได้")
    void delete_user_permissions() throws Exception {
        mvc.perform(delete(BASE + "/" + userId).with(asUser(otherId)))
                .andExpect(status().isForbidden());

        mvc.perform(delete(BASE + "/" + userId).with(asUser(userId)))
                .andExpect(status().is2xxSuccessful());

        assertThat(users.findById(userId)).isEmpty();
    }

    // ---------- ERROR & EDGE CASES ----------

    @Test
    @DisplayName("GET /api/users/{id} -> 404 เมื่อไม่พบ")
    void get_not_found() throws Exception {
        mvc.perform(get(BASE + "/999999").with(asAdmin(adminId)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/users (JSON) -> duplicate email -> 409/4xx (ต้องระบุ password ด้วย)")
    void create_duplicate_email() throws Exception {
        String req = """
            {"email":"me@example.com","userName":"dup","firstName":"Dup","lastName":"User","password":"pass123","role":1}
            """;

        mvc.perform(post(BASE)
                        .with(asAdmin(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().is4xxClientError());
    }
}
