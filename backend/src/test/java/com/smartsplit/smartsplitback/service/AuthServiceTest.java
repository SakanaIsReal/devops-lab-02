package com.smartsplit.smartsplitback.service;

import com.smartsplit.smartsplitback.model.Role;
import com.smartsplit.smartsplitback.model.User;
import com.smartsplit.smartsplitback.model.dto.AuthResponse;
import com.smartsplit.smartsplitback.model.dto.LoginRequest;
import com.smartsplit.smartsplitback.model.dto.RegisterRequest;
import com.smartsplit.smartsplitback.repository.UserRepository;
import com.smartsplit.smartsplitback.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    @Mock private UserRepository users;
    @Mock private PasswordEncoder encoder;
    @Mock private JwtService jwt;

    @InjectMocks private AuthService authService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private static User newUser(Long id, String email, String userName, String phone, String passwordHash, Role role) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setUserName(userName);
        u.setPhone(phone);
        u.setPasswordHash(passwordHash);
        u.setRole(role);
        return u;
    }

    @Nested
    @DisplayName("register(...)")
    class RegisterTests {

        @Test
        @DisplayName("สร้างผู้ใช้ใหม่ -> encode -> save -> JWT(86400s) -> AuthResponse ถูกต้อง (พร้อม first/last name)")
        void register_success() {
            RegisterRequest req = new RegisterRequest(
                    "alice@example.com",
                    "Alice",
                    "0990000000",
                    "Passw0rd!",
                    " Alice  ",
                    "  Liddell "
            );

            when(users.findByEmail("alice@example.com")).thenReturn(Optional.empty());
            when(encoder.encode("Passw0rd!")).thenReturn("HASHED_PASS");

            doAnswer(inv -> {
                User u = inv.getArgument(0, User.class);
                u.setId(1L);
                return u;
            }).when(users).save(any(User.class));

            when(jwt.generate(anyString(), ArgumentMatchers.<String, Object>anyMap(), anyLong()))
                    .thenReturn("fake-jwt");

            AuthResponse resp = authService.register(req);

            assertThat(resp).isNotNull();
            assertThat(resp.accessToken()).isEqualTo("fake-jwt");
            assertThat(resp.tokenType()).isEqualTo("Bearer");
            assertThat(resp.userId()).isEqualTo(1L);
            assertThat(resp.email()).isEqualTo("alice@example.com");
            assertThat(resp.userName()).isEqualTo("Alice");
            assertThat(resp.role()).isEqualTo(Role.USER.code());
            assertThat(resp.phone()).isEqualTo("0990000000");

            ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
            verify(users).save(userCap.capture());
            User saved = userCap.getValue();
            assertThat(saved.getFirstName()).isEqualTo("Alice");
            assertThat(saved.getLastName()).isEqualTo("Liddell");

            verify(users).findByEmail("alice@example.com");
            verify(encoder).encode("Passw0rd!");
            verify(jwt).generate(anyString(), anyMap(), eq(60L * 60L * 24L));

            ArgumentCaptor<String> subCap = ArgumentCaptor.forClass(String.class);
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> claimsCap = ArgumentCaptor.forClass(Map.class);
            ArgumentCaptor<Long> ttlCap = ArgumentCaptor.forClass(Long.class);

            verify(jwt).generate(subCap.capture(), claimsCap.capture(), ttlCap.capture());
            assertThat(subCap.getValue()).isEqualTo("1");
            assertThat(ttlCap.getValue()).isEqualTo(86400L);

            Map<String, Object> claims = claimsCap.getValue();
            assertThat(claims).containsEntry("uid", 1L);
            assertThat(claims).containsEntry("email", "alice@example.com");
            assertThat(claims).containsEntry("userName", "Alice");
            assertThat(claims).containsEntry("role", Role.USER.code());
        }

        @Test
        @DisplayName("trim/blank: firstName/lastName เป็นช่องว่าง -> ถูกบันทึกเป็น null")
        void register_trims_blank_names_to_null() {
            RegisterRequest req = new RegisterRequest(
                    "trim@example.com",
                    "TrimUser",
                    "0888888888",
                    "S3cret!",
                    "   ",
                    ""
            );

            when(users.findByEmail("trim@example.com")).thenReturn(Optional.empty());
            when(encoder.encode("S3cret!")).thenReturn("HASH_TRIM");
            doAnswer(inv -> {
                User u = inv.getArgument(0, User.class);
                u.setId(77L);
                return u;
            }).when(users).save(any(User.class));
            when(jwt.generate(anyString(), anyMap(), anyLong())).thenReturn("jwt-trim");

            AuthResponse resp = authService.register(req);

            assertThat(resp.userId()).isEqualTo(77L);

            ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
            verify(users).save(cap.capture());
            User saved = cap.getValue();
            assertThat(saved.getFirstName()).isNull();
            assertThat(saved.getLastName()).isNull();
        }

        @Test
        @DisplayName("อีเมลซ้ำ -> 409 และไม่เรียก encode/save/jwt")
        void register_duplicateEmail_conflict() {
            RegisterRequest req = new RegisterRequest(
                    "dup@example.com",
                    "Dup",
                    "0911111111",
                    "p@ss",
                    null,
                    null
            );

            when(users.findByEmail("dup@example.com"))
                    .thenReturn(Optional.of(newUser(99L, "dup@example.com", "DupX", "099", "HASH", Role.USER)));

            ResponseStatusException ex = catchThrowableOfType(() -> authService.register(req), ResponseStatusException.class);
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.CONFLICT.value());
            assertThat(ex.getReason()).isEqualTo("Email already in use");

            verify(users).findByEmail("dup@example.com");
            verify(encoder, never()).encode(anyString());
            verify(users, never()).save(any());
            verify(jwt, never()).generate(anyString(), anyMap(), anyLong());
        }

        @Test
        @DisplayName("รหัสผ่านมีอักขระพิเศษ/ยาวมาก -> encode ถูกเรียก และ save สำเร็จ")
        void register_password_edge_cases() {
            String longPw = "Aa!" + "x".repeat(200);
            RegisterRequest req = new RegisterRequest(
                    "edge@example.com",
                    "Edge",
                    "0800000000",
                    longPw,
                    "Edge",
                    "Case"
            );

            when(users.findByEmail("edge@example.com")).thenReturn(Optional.empty());
            when(encoder.encode(longPw)).thenReturn("HASH_LONG");
            doAnswer(inv -> {
                User u = inv.getArgument(0, User.class);
                u.setId(555L);
                return u;
            }).when(users).save(any(User.class));
            when(jwt.generate(anyString(), anyMap(), anyLong())).thenReturn("jwt-edge");

            AuthResponse resp = authService.register(req);
            assertThat(resp.userId()).isEqualTo(555L);

            verify(encoder).encode(longPw);
            verify(users).save(any(User.class));
            verify(jwt).generate(eq("555"), anyMap(), eq(86400L));
        }
    }

    @Nested
    @DisplayName("login(...)")
    class LoginTests {

        @Test
        @DisplayName("USER: อีเมล/รหัสถูกต้อง -> JWT(86400s) + AuthResponse")
        void login_success_user() {
            LoginRequest req = new LoginRequest("bob@example.com", "P@ss1234");

            User persisted = newUser(2L, "bob@example.com", "Bob", "0900000000", "HASHED_X", Role.USER);
            when(users.findByEmail("bob@example.com")).thenReturn(Optional.of(persisted));
            when(encoder.matches("P@ss1234", "HASHED_X")).thenReturn(true);
            when(jwt.generate(anyString(), ArgumentMatchers.<String, Object>anyMap(), anyLong()))
                    .thenReturn("jwt-login");

            AuthResponse resp = authService.login(req);

            assertThat(resp.accessToken()).isEqualTo("jwt-login");
            assertThat(resp.tokenType()).isEqualTo("Bearer");
            assertThat(resp.userId()).isEqualTo(2L);
            assertThat(resp.email()).isEqualTo("bob@example.com");
            assertThat(resp.userName()).isEqualTo("Bob");
            assertThat(resp.role()).isEqualTo(Role.USER.code());
            assertThat(resp.phone()).isEqualTo("0900000000");

            verify(users).findByEmail("bob@example.com");
            verify(encoder).matches("P@ss1234", "HASHED_X");

            ArgumentCaptor<String> subCap = ArgumentCaptor.forClass(String.class);
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> claimsCap = ArgumentCaptor.forClass(Map.class);
            ArgumentCaptor<Long> ttlCap = ArgumentCaptor.forClass(Long.class);

            verify(jwt).generate(subCap.capture(), claimsCap.capture(), ttlCap.capture());
            assertThat(subCap.getValue()).isEqualTo("2");
            assertThat(ttlCap.getValue()).isEqualTo(60L * 60L * 24L);

            Map<String, Object> claims = claimsCap.getValue();
            assertThat(claims).containsEntry("uid", 2L);
            assertThat(claims).containsEntry("email", "bob@example.com");
            assertThat(claims).containsEntry("userName", "Bob");
            assertThat(claims).containsEntry("role", Role.USER.code());
        }

        @Test
        @DisplayName("ADMIN: role ต้องสะท้อนเป็น ADMIN.code() ใน JWT/response")
        void login_success_admin_role() {
            LoginRequest req = new LoginRequest("root@example.com", "Sup3r!");

            User admin = newUser(42L, "root@example.com", "Root", "0700000000", "HASHED_ADMIN", Role.ADMIN);
            when(users.findByEmail("root@example.com")).thenReturn(Optional.of(admin));
            when(encoder.matches("Sup3r!", "HASHED_ADMIN")).thenReturn(true);
            when(jwt.generate(anyString(), anyMap(), anyLong())).thenReturn("jwt-admin");

            AuthResponse resp = authService.login(req);

            assertThat(resp.accessToken()).isEqualTo("jwt-admin");
            assertThat(resp.role()).isEqualTo(Role.ADMIN.code());

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> claimsCap = ArgumentCaptor.forClass(Map.class);
            verify(jwt).generate(eq("42"), claimsCap.capture(), eq(86400L));
            assertThat(claimsCap.getValue()).containsEntry("role", Role.ADMIN.code());
        }

        @Test
        @DisplayName("อีเมลไม่พบ -> 401 UNAUTHORIZED")
        void login_emailNotFound_unauthorized() {
            LoginRequest req = new LoginRequest("noone@example.com", "whatever");
            when(users.findByEmail("noone@example.com")).thenReturn(Optional.empty());

            ResponseStatusException ex = catchThrowableOfType(() -> authService.login(req), ResponseStatusException.class);
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
            assertThat(ex.getReason()).isEqualTo("Invalid credentials");

            verify(users).findByEmail("noone@example.com");
            verify(encoder, never()).matches(anyString(), anyString());
            verify(jwt, never()).generate(anyString(), anyMap(), anyLong());
        }

        @Test
        @DisplayName("รหัสผ่านผิด -> 401 UNAUTHORIZED")
        void login_wrongPassword_unauthorized() {
            LoginRequest req = new LoginRequest("bob@example.com", "wrong");
            User persisted = newUser(2L, "bob@example.com", "Bob", "0900000000", "HASHED_X", Role.USER);

            when(users.findByEmail("bob@example.com")).thenReturn(Optional.of(persisted));
            when(encoder.matches("wrong", "HASHED_X")).thenReturn(false);

            ResponseStatusException ex = catchThrowableOfType(() -> authService.login(req), ResponseStatusException.class);
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
            assertThat(ex.getReason()).isEqualTo("Invalid credentials");

            verify(users).findByEmail("bob@example.com");
            verify(encoder).matches("wrong", "HASHED_X");
            verify(jwt, never()).generate(anyString(), anyMap(), anyLong());
        }
    }
}
