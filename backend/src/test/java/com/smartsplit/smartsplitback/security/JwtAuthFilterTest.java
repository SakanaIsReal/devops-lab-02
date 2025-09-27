package com.smartsplit.smartsplitback.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JwtAuthFilterTest {

    @Mock private JwtService jwtService;
    @Mock private FilterChain chain;

    private JwtAuthFilter filter;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        filter = new JwtAuthFilter(jwtService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
        mocks.close();
    }


    private MockHttpServletRequest reqWithHeader(String token) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return req;
    }


    @Test
    @DisplayName("มี authentication อยู่แล้ว → filter ข้าม ไม่แตะ jwtService")
    void alreadyAuthenticated_skip() throws ServletException, IOException {
        var auth = new UsernamePasswordAuthenticationToken("999", null, null);
        SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
        verifyNoInteractions(jwtService);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(auth);
    }


    @Test
    @DisplayName("ไม่มี Authorization header → chain ต่อไปเฉย ๆ")
    void noHeader() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, chain);

        verify(chain).doFilter(req, res);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("Authorization ไม่ขึ้นต้นด้วย Bearer → chain ต่อไปเฉย ๆ")
    void nonBearerHeader() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(HttpHeaders.AUTHORIZATION, "Basic abc");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, chain);

        verify(chain).doFilter(req, res);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtService);
    }


    @Test
    @DisplayName("token valid: uid จาก claim + role = ADMIN → set auth principal=uid และ ROLE_ADMIN")
    void validToken_uidFromClaim_adminRole() throws Exception {
        String token = "t1";
        MockHttpServletRequest req = reqWithHeader(token);
        MockHttpServletResponse res = new MockHttpServletResponse();

        when(jwtService.getUserId(token)).thenReturn(123L);
        when(jwtService.getRoleCode(token)).thenReturn(JwtService.ROLE_ADMIN);

        filter.doFilterInternal(req, res, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo("123"); // principal เป็น String ของ uid
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");

        verify(jwtService).getUserId(token);
        verify(jwtService).getRoleCode(token);
        verify(chain).doFilter(req, res);
    }


    @Test
    @DisplayName("uid เป็น null → subject เป็นตัวเลข → set auth (ROLE_USER เป็นค่า default เมื่อ roleCode=null)")
    void uidNull_subjectNumeric_defaultUserRole() throws Exception {
        String token = "t2";
        MockHttpServletRequest req = reqWithHeader(token);
        MockHttpServletResponse res = new MockHttpServletResponse();

        when(jwtService.getUserId(token)).thenReturn(null);
        when(jwtService.getSubject(token)).thenReturn("456");
        when(jwtService.getRoleCode(token)).thenReturn(null); // ไม่มี role → USER

        filter.doFilterInternal(req, res, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo("456");
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");

        verify(jwtService).getUserId(token);
        verify(jwtService).getSubject(token);
        verify(jwtService).getRoleCode(token);
        verify(chain).doFilter(req, res);
    }


    @Test
    @DisplayName("uid=null และ subject ไม่ใช่ตัวเลข → ไม่ตั้ง authentication")
    void uidNull_subjectNonNumeric_noAuth() throws Exception {
        String token = "t3";
        MockHttpServletRequest req = reqWithHeader(token);
        MockHttpServletResponse res = new MockHttpServletResponse();

        when(jwtService.getUserId(token)).thenReturn(null);
        when(jwtService.getSubject(token)).thenReturn("abc-xyz"); // ไม่ใช่ตัวเลข

        filter.doFilterInternal(req, res, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        verify(jwtService).getUserId(token);
        verify(jwtService).getSubject(token);
        verify(chain).doFilter(req, res);
        verify(jwtService, never()).getRoleCode(any());
    }


    @Test
    @DisplayName("jwtService โยน exception → ฟิลเตอร์กลืน error และปล่อย chain ทำงานต่อ")
    void jwtServiceThrows_isSwallowed() throws Exception {
        String token = "t4";
        MockHttpServletRequest req = reqWithHeader(token);
        MockHttpServletResponse res = new MockHttpServletResponse();

        when(jwtService.getUserId(token)).thenThrow(new RuntimeException("boom"));
        when(jwtService.getSubject(token)).thenThrow(new RuntimeException("boom2"));

        filter.doFilterInternal(req, res, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(req, res);
    }


    @Test
    @DisplayName("getUserId() พัง แต่ subject เป็นตัวเลข และ role=ADMIN → ยังตั้ง auth ได้")
    void userIdThrows_subjectNumeric_adminRole() throws Exception {
        String token = "t5";
        MockHttpServletRequest req = reqWithHeader(token);
        MockHttpServletResponse res = new MockHttpServletResponse();

        when(jwtService.getUserId(token)).thenThrow(new RuntimeException("x"));
        when(jwtService.getSubject(token)).thenReturn("9001");
        when(jwtService.getRoleCode(token)).thenReturn(JwtService.ROLE_ADMIN);

        filter.doFilterInternal(req, res, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo("9001");
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
        verify(chain).doFilter(req, res);
    }


    @Test
    @DisplayName("รหัสผ่านใน Authentication เป็น null เสมอ (ตามที่ฟิลเตอร์ตั้ง)")
    void credentialsNull() throws Exception {
        String token = "t6";
        MockHttpServletRequest req = reqWithHeader(token);
        MockHttpServletResponse res = new MockHttpServletResponse();

        when(jwtService.getUserId(token)).thenReturn(77L);
        when(jwtService.getRoleCode(token)).thenReturn(null);

        filter.doFilterInternal(req, res, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getCredentials()).isNull();
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
        verify(chain).doFilter(req, res);
    }


    @Test
    @DisplayName("ตั้ง Value ใน SecurityContextHolder ตามรายละเอียดที่คาดหวัง")
    void setsSecurityContextDetails() throws Exception {
        String token = "t7";
        MockHttpServletRequest req = reqWithHeader(token);
        MockHttpServletResponse res = new MockHttpServletResponse();

        when(jwtService.getUserId(token)).thenReturn(1L);
        when(jwtService.getRoleCode(token)).thenReturn(null);

        filter.doFilterInternal(req, res, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(auth.getPrincipal()).isEqualTo("1");

        // ตรวจว่าใส่ details (WebAuthenticationDetailsSource) แล้ว
        assertThat(auth.getDetails()).isNotNull();

        verify(chain).doFilter(req, res);
    }
}
