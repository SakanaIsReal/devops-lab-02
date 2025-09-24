package com.smartsplit.smartsplitback.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SecurityFacade")
class SecurityFacadeTest {

    @Mock JwtService jwtService;

    SecurityFacade facade;

    @BeforeEach
    void setUp() {
        facade = new SecurityFacade(jwtService);
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    // ---------- helpers ----------
    private void setAuth(Object principal, String... roles) {
        var authorities = (roles == null) ? List.<SimpleGrantedAuthority>of()
                : java.util.Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList();
        var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void setRequest(MockHttpServletRequest req) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    }

    // ---------- currentUserId ----------
    @Nested
    @DisplayName("currentUserId()")
    class CurrentUserId {

        @Test
        @DisplayName("principal เป็นตัวเลข (String) → แปลงเป็น Long ตรง ๆ และไม่แตะ JwtService")
        void numericPrincipal_returnsId() {
            setAuth("12345");
            Long uid = facade.currentUserId();
            assertThat(uid).isEqualTo(12345L);
            verifyNoInteractions(jwtService);
        }

        @Test
        @DisplayName("principal ไม่ใช่ตัวเลข → ดึงจาก Bearer token (uid ใน claim) ได้")
        void nonNumericPrincipal_readFromBearerToken() {
            setAuth("alice@example.com");

            MockHttpServletRequest req = new MockHttpServletRequest();
            req.addHeader("Authorization", "Bearer token-xyz");
            setRequest(req);

            when(jwtService.getUserId("token-xyz")).thenReturn(77L);

            Long uid = facade.currentUserId();
            assertThat(uid).isEqualTo(77L);
            verify(jwtService).getUserId("token-xyz");
        }

        @Test
        @DisplayName("principal ไม่ใช่ตัวเลข + Bearer มีช่องว่าง → trim แล้วอ่าน uid ได้")
        void bearerToken_trimmed() {
            setAuth("someone@host");
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.addHeader("Authorization", "Bearer   abc123   ");
            setRequest(req);

            when(jwtService.getUserId("abc123")).thenReturn(9L);

            Long uid = facade.currentUserId();
            assertThat(uid).isEqualTo(9L);
            verify(jwtService).getUserId("abc123");
        }

        @Test
        @DisplayName("ไม่มี auth ใน context → คืน null")
        void noAuth_returnsNull() {
            // no auth, no request
            Long uid = facade.currentUserId();
            assertThat(uid).isNull();
            verifyNoInteractions(jwtService);
        }

        @Test
        @DisplayName("principal ไม่ใช่ตัวเลข + ไม่มี Bearer header → คืน null")
        void nonNumeric_noBearer_returnsNull() {
            setAuth("bob@example.com");
            // ไม่ตั้ง RequestContextHolder
            Long uid = facade.currentUserId();
            assertThat(uid).isNull();
            verifyNoInteractions(jwtService);
        }

        @Test
        @DisplayName("principal ไม่ใช่ตัวเลข + Bearer มี แต่ JwtService คืน null → คืน null")
        void nonNumeric_bearer_butJwtNull_returnsNull() {
            setAuth("user@x");
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.addHeader("Authorization", "Bearer t0");
            setRequest(req);

            when(jwtService.getUserId("t0")).thenReturn(null);

            Long uid = facade.currentUserId();
            assertThat(uid).isNull();
            verify(jwtService).getUserId("t0");
        }
    }

    // ---------- hasRole / isAdmin ----------
    @Nested
    @DisplayName("hasRole() / isAdmin()")
    class Roles {

        @Test
        @DisplayName("มี ROLE_USER → hasRole(\"ROLE_USER\") = true, ROLE_ADMIN = false")
        void hasRole_user() {
            setAuth("123", "ROLE_USER");
            assertThat(facade.hasRole("ROLE_USER")).isTrue();
            assertThat(facade.hasRole("ROLE_ADMIN")).isFalse();
            assertThat(facade.isAdmin()).isFalse();
        }

        @Test
        @DisplayName("มี ROLE_ADMIN → isAdmin() = true และ hasRole(\"ROLE_ADMIN\") = true")
        void hasRole_admin() {
            setAuth("1", "ROLE_ADMIN");
            assertThat(facade.isAdmin()).isTrue();
            assertThat(facade.hasRole("ROLE_ADMIN")).isTrue();
        }

        @Test
        @DisplayName("ไม่มี auth → hasRole/isAdmin = false")
        void noAuth_rolesFalse() {
            assertThat(facade.hasRole("ROLE_USER")).isFalse();
            assertThat(facade.isAdmin()).isFalse();
        }
    }
}
