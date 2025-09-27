
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

    // ---------- Helpers ----------
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

    // ================== REGISTER ==================
    @Nested
    @DisplayName("register(...)")
    class RegisterTests {

        @Test
        @DisplayName("สร้างผู้ใช้ใหม่ -> encode -> save -> JWT(86400s) -> AuthResponse ถูกต้อง")
        void register_success() {
          
            RegisterRequest req = new RegisterRequest(
                    "alice@example.com", "Passw0rd!", "Alice", "0990000000"
            );

            when(users.findByEmail("alice@example.com")).thenReturn(Optional.empty());
            when(encoder.encode("Passw0rd!")).thenReturn("HASHED_PASS");

            // จำลอง behavior ของ JPA save: ใส่ id กลับเข้าไปใน entity เดิม
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

            verify(users).findByEmail("alice@example.com");
            verify(encoder).encode("Passw0rd!");
            verify(users).save(argThat(u ->
                    u.getEmail().equals("alice@example.com")
                            && u.getUserName().equals("Alice")
                            && u.getPhone().equals("0990000000")
                            && u.getPasswordHash().equals("HASHED_PASS")
                            && u.getRole() == Role.USER
            ));

            ArgumentCaptor<String> subCap = ArgumentCaptor.forClass(String.class);
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> claimsCap = ArgumentCaptor.forClass(Map.class);
            ArgumentCaptor<Long> ttlCap = ArgumentCaptor.forClass(Long.class);

            verify(jwt).generate(subCap.capture(), claimsCap.capture(), ttlCap.capture());
            assertThat(subCap.getValue()).isEqualTo("1");
            assertThat(ttlCap.getValue()).isEqualTo(60L * 60L * 24L);

            Map<String, Object> claims = claimsCap.getValue();
            assertThat(claims).containsEntry("uid", 1L);
            assertThat(claims).containsEntry("email", "alice@example.com");
            assertThat(claims).containsEntry("userName", "Alice");
            assertThat(claims).containsEntry("role", Role.USER.code());
        }

        @Test
        @DisplayName("email ซ้ำ -> 409 CONFLICT และไม่เรียก encode/save/jwt")
        void register_duplicateEmail_conflict() {
           
            RegisterRequest req = new RegisterRequest(
                    "dup@example.com", "p@ss", "Dup", "0911111111"
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
    }

    // ================== LOGIN ==================
    @Nested
    @DisplayName("login(...)")
    class LoginTests {

        @Test
        @DisplayName("email/รหัสถูกต้อง -> JWT(86400s) + AuthResponse")
        void login_success() {
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
        @DisplayName("email ไม่พบ -> 401 UNAUTHORIZED")
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
        @DisplayName("password ผิด -> 401 UNAUTHORIZED")
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
