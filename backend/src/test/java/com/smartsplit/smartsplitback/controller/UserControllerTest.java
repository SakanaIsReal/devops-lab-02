package com.smartsplit.smartsplitback.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartsplit.smartsplitback.model.User;
import com.smartsplit.smartsplitback.model.dto.UserDto;
import com.smartsplit.smartsplitback.repository.GroupMemberRepository;
import com.smartsplit.smartsplitback.security.JwtAuthFilter;
import com.smartsplit.smartsplitback.security.JwtService;
import com.smartsplit.smartsplitback.security.Perms;
import com.smartsplit.smartsplitback.service.FileStorageService;
import com.smartsplit.smartsplitback.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SuppressWarnings("removal")
@WebMvcTest(controllers = UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({UserControllerTest.MethodSecurityConfig.class, UserControllerTest.MethodSecurityAdvice.class})
class UserControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfig {}

    @RestControllerAdvice
    static class MethodSecurityAdvice {
        @ExceptionHandler({AuthorizationDeniedException.class, AuthenticationCredentialsNotFoundException.class})
        @ResponseStatus(HttpStatus.FORBIDDEN)
        void handle403() {}
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;


    @MockitoBean JwtAuthFilter jwtAuthFilter;
    @MockitoBean JwtService jwtService;

    @MockitoBean(name = "perm", answers = Answers.RETURNS_DEFAULTS)
    Perms perm;

    @MockitoBean UserService svc;
    @MockitoBean FileStorageService storage;
    @MockitoBean GroupMemberRepository members;

    private User user(long id, String email, String name, String phone, String avatar, String qr) {
        var u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setUserName(name);
        u.setPhone(phone);
        u.setAvatarUrl(avatar);
        u.setQrCodeUrl(qr);
        return u;
    }

    // ---------- GET /api/users (admin only) ----------
    @Test
    @DisplayName("GET /api/users -> 403 เมื่อไม่ใช่แอดมิน, 200 เมื่อเป็นแอดมิน")
    void list_admin_only() throws Exception {
        when(perm.isAdmin()).thenReturn(false);
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isForbidden());

        when(perm.isAdmin()).thenReturn(true);
        when(svc.list()).thenReturn(List.of(
                user(1, "a@x.com", "A", "1", "av1", "qr1"),
                user(2, "b@x.com", "B", "2", "av2", "qr2")
        ));
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].id").value(2));
    }

    // ---------- GET /api/users/{id} (canViewUser) ----------
    @Test
    @DisplayName("GET /api/users/{id} -> 403 เมื่อไม่มีสิทธิ์, 404 เมื่อไม่พบ, 200 เมื่อสำเร็จ")
    void get_authorization_and_notfound_ok() throws Exception {
        when(perm.canViewUser(55L)).thenReturn(false);
        mockMvc.perform(get("/api/users/55"))
                .andExpect(status().isForbidden());

        when(perm.canViewUser(55L)).thenReturn(true);
        when(svc.get(55L)).thenReturn(null);
        mockMvc.perform(get("/api/users/55"))
                .andExpect(status().isNotFound())
                .andExpect(status().reason("User not found"));

        when(svc.get(55L)).thenReturn(user(55, "c@x.com", "C", "3", "av", "qr"));
        mockMvc.perform(get("/api/users/55"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(55))
                .andExpect(jsonPath("$.email").value("c@x.com"));
    }

    // ---------- GET /api/users/me ----------
    @Test
    @DisplayName("GET /api/users/me -> 403 เมื่่อไม่ authenticated (ไม่มี @WithMockUser)")
    void me_forbidden_when_not_authenticated() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/users/me -> 401 เมื่อ currentUserId เป็น null, 404 เมื่อไม่พบ user, 200 เมื่อสำเร็จ")
    void me_unauthorized_notfound_ok() throws Exception {
        when(perm.currentUserId()).thenReturn(null);
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(status().reason("Unauthorized"));

        when(perm.currentUserId()).thenReturn(77L);
        when(svc.get(77L)).thenReturn(null);
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isNotFound())
                .andExpect(status().reason("User not found"));

        when(svc.get(77L)).thenReturn(user(77, "me@x.com", "Me", "9", "av", "qr"));
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(77))
                .andExpect(jsonPath("$.email").value("me@x.com"));
    }

    // ---------- GET /api/users/search ----------
    @Test
    @DisplayName("GET /api/users/search -> 403 เมื่อไม่ authenticated, 200 เมื่อสำเร็จ และตัด qrCodeUrl ออก")
    void search_auth_and_ok() throws Exception {
        mockMvc.perform(get("/api/users/search").param("q", "a"))
                .andExpect(status().isForbidden());

        when(perm.isAdmin()).thenReturn(false);
        when(svc.searchByName("a")).thenReturn(List.of(
                user(1, "a@x.com", "A", "1", "av1", "qr1"),
                user(2, "aa@x.com", "AA", "2", "av2", "qr2")
        ));

        mockMvc.perform(get("/api/users/search").param("q", "a").with(request -> request))
                .andExpect(status().isForbidden()); // ยังไม่มี @WithMockUser
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/users/search -> 200 เมื่อ authenticated")
    void search_ok_when_authenticated() throws Exception {
        when(svc.searchByName("a")).thenReturn(List.of(
                user(1, "a@x.com", "A", "1", "av1", "qr1"),
                user(2, "aa@x.com", "AA", "2", "av2", "qr2")
        ));
        mockMvc.perform(get("/api/users/search").param("q", "a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].qrCodeUrl").doesNotExist());
    }

    // ---------- POST /api/users (multipart, admin) ----------
    @Test
    @DisplayName("POST /api/users (multipart) -> 403 เมื่อไม่ใช่ admin, 201 เมื่อสำเร็จ พร้อมอัปโหลดไฟล์")
    void create_multipart_auth_and_created() throws Exception {
        // 403 เมื่อไม่ใช่ admin
        when(perm.isAdmin()).thenReturn(false);
        var userPartForbidden = new MockMultipartFile(
                "user", "", "application/json",
                objectMapper.writeValueAsBytes(new UserDto(null, "n@x.com", "New", "09", null, null)));
        mockMvc.perform(multipart("/api/users").file(userPartForbidden))
                .andExpect(status().isForbidden());

        // 201 เมื่อสำเร็จ (ต้องส่ง password และใช้พาร์ท "qrCode")
        when(perm.isAdmin()).thenReturn(true);
        when(svc.create(any(User.class))).thenAnswer((Answer<User>) inv -> {
            User u = inv.getArgument(0);
            u.setId(100L);
            return u;
        });
        when(svc.update(any(User.class))).thenAnswer((Answer<User>) inv -> inv.getArgument(0));
        when(storage.save(any(), eq("avatars"), eq("user-100"), any())).thenReturn("http://files/av-100.png");
        when(storage.save(any(), eq("qrcodes"), eq("qr-100"), any())).thenReturn("http://files/qr-100.png");

        // JSON ของ UserCreateRequest (ต้องมี password)
        String userJson = """
            {"email":"n@x.com","userName":"New","phone":"09","avatarUrl":null,"qrCodeUrl":null,"password":"pass123","role":1}
            """;
        MockMultipartFile userPart = new MockMultipartFile("user", "", "application/json", userJson.getBytes());
        MockMultipartFile avatar = new MockMultipartFile("avatar", "a.png", "image/png", new byte[]{1});
        MockMultipartFile qrCode = new MockMultipartFile("qrCode", "q.png", "image/png", new byte[]{2});

        mockMvc.perform(multipart("/api/users")
                        .file(userPart).file(avatar).file(qrCode))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.avatarUrl").value("http://files/av-100.png"))
                .andExpect(jsonPath("$.qrCodeUrl").value("http://files/qr-100.png"));
    }

    // ---------- POST /api/users (json, admin) ----------
    @Test
    @DisplayName("POST /api/users (json) -> 403 เมื่อไม่ใช่ admin, 201 เมื่อสำเร็จ")
    void create_json_auth_and_created() throws Exception {
        // 403 เมื่อไม่ใช่ admin
        when(perm.isAdmin()).thenReturn(false);
        String inForbidden = """
            {"email":"n@x.com","userName":"New","phone":"09","avatarUrl":"av0","qrCodeUrl":"qr0","password":"pass123","role":1}
            """;
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content(inForbidden))
                .andExpect(status().isForbidden());

        // 201 เมื่อสำเร็จ (ต้องมี password)
        when(perm.isAdmin()).thenReturn(true);
        when(svc.create(any(User.class))).thenAnswer((Answer<User>) inv -> {
            User u = inv.getArgument(0);
            u.setId(101L);
            return u;
        });

        String in = """
            {"email":"n@x.com","userName":"New","phone":"09","avatarUrl":"av0","qrCodeUrl":"qr0","password":"pass123","role":1}
            """;
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content(in))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.email").value("n@x.com"));
    }

    // ---------- PUT /api/users/{id} (json, admin or self) ----------
    @Test
    @DisplayName("PUT /api/users/{id} (json) -> 403 เมื่อไม่ใช่ admin หรือ self, 404 เมื่อไม่พบ, 200 เมื่อสำเร็จ")
    void update_json_auth_and_notfound_ok() throws Exception {
        when(perm.isAdmin()).thenReturn(false);
        when(perm.isSelf(55L)).thenReturn(false);

        var in = new UserDto(null, "u@x.com", "U", "08", "av", "qr");
        mockMvc.perform(put("/api/users/55").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(in)))
                .andExpect(status().isForbidden());

        when(perm.isSelf(55L)).thenReturn(true);
        when(svc.get(55L)).thenReturn(null);
        mockMvc.perform(put("/api/users/55").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(in)))
                .andExpect(status().isNotFound())
                .andExpect(status().reason("User not found"));

        when(svc.get(55L)).thenReturn(user(55, "old@x.com", "Old", "07", "av0", "qr0"));
        when(svc.update(any(User.class))).thenAnswer((Answer<User>) inv -> inv.getArgument(0));
        mockMvc.perform(put("/api/users/55").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(in)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("u@x.com"))
                .andExpect(jsonPath("$.userName").value("U"))
                .andExpect(jsonPath("$.phone").value("08"));
    }

    @Test
    @DisplayName("PUT /api/users/{id} (multipart) -> 403, 404, 200 และเปลี่ยนรูป")
    void update_multipart_auth_and_ok() throws Exception {
        when(perm.isAdmin()).thenReturn(false);
        when(perm.isSelf(55L)).thenReturn(false);

        var userPart = new MockMultipartFile(
                "user", "", "application/json",
                objectMapper.writeValueAsBytes(new UserDto(null, "u@x.com", "U", "08", null, null)));

        // ไม่มีสิทธิ์
        mockMvc.perform(multipart("/api/users/55").file(userPart)
                        .with(req -> { req.setMethod("PUT"); return req; }))
                .andExpect(status().isForbidden());

        // ไม่พบ
        when(perm.isSelf(55L)).thenReturn(true);
        when(svc.get(55L)).thenReturn(null);
        mockMvc.perform(multipart("/api/users/55").file(userPart)
                        .with(req -> { req.setMethod("PUT"); return req; }))
                .andExpect(status().isNotFound())
                .andExpect(status().reason("User not found"));

        // สำเร็จ และเปลี่ยนรูป
        var existing = user(55, "old@x.com", "Old", "07", "http://old/av.png", "http://old/qr.png");
        when(svc.get(55L)).thenReturn(existing);
        when(storage.save(any(), eq("avatars"), eq("user-55"), any())).thenReturn("http://files/av-55.png");
        when(storage.save(any(), eq("qrcodes"), eq("qr-55"), any())).thenReturn("http://files/qr-55.png");
        when(svc.update(any(User.class))).thenAnswer((Answer<User>) inv -> inv.getArgument(0));

        MockMultipartFile avatar = new MockMultipartFile("avatar", "a.png", "image/png", new byte[]{1});
        // 🔧 ชื่อพาร์ทฝั่ง Controller ใช้ "qr" (ไม่ใช่ "qrCode")
        MockMultipartFile qr     = new MockMultipartFile("qr", "q.png", "image/png", new byte[]{2});

        mockMvc.perform(multipart("/api/users/55")
                        .file(userPart).file(avatar).file(qr)
                        .with(req -> { req.setMethod("PUT"); return req; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value("http://files/av-55.png"))
                .andExpect(jsonPath("$.qrCodeUrl").value("http://files/qr-55.png"));
    }


    // ---------- DELETE /api/users/{id} (admin or self) ----------
    @Test
    @DisplayName("DELETE /api/users/{id} -> 403 เมื่อไม่มีสิทธิ์, 404 เมื่อไม่พบ, 204 เมื่อสำเร็จ")
    void delete_auth_and_notfound_ok() throws Exception {
        when(perm.isAdmin()).thenReturn(false);
        when(perm.isSelf(55L)).thenReturn(false);
        mockMvc.perform(delete("/api/users/55"))
                .andExpect(status().isForbidden());

        when(perm.isSelf(55L)).thenReturn(true);
        when(svc.get(55L)).thenReturn(null);
        mockMvc.perform(delete("/api/users/55"))
                .andExpect(status().isNotFound())
                .andExpect(status().reason("User not found"));

        when(svc.get(55L)).thenReturn(user(55, "x@x.com", "X", "0", "a", "q"));
        mockMvc.perform(delete("/api/users/55"))
                .andExpect(status().isNoContent());
    }
}
