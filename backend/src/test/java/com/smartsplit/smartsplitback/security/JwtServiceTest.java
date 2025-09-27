package com.smartsplit.smartsplitback.security;

import com.smartsplit.smartsplitback.model.Role;
import com.smartsplit.smartsplitback.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {

    // ====== Test Secret (>= 256-bit; base64) ======
    // NOTE: ใช้เฉพาะในเทสต์เท่านั้น ไม่เกี่ยวกับค่าในโปรไฟล์จริง
    private static final String TEST_SECRET_B64 =
            "b6m2mP9lq7WcC0oQ3k2b7zN0F3j4L9Fqf1g0G6v7H8i9J2k3L4m5n6o7p8q9r0s1t2u3v4w5x6y7z8A9B0C1D2E3F4G5H6I7";

    private static SecretKey testKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET_B64));
    }

    // ====== Test Double ของ JwtKeyProvider ======
    // ให้คืน SecretKey คงที่สำหรับเทสต์
    private static class TestJwtKeyProvider extends JwtKeyProvider {
        @Override
        public SecretKey getHmacKey() {
            return testKey();
        }
    }

    // สร้าง JwtService โดยอัด TestJwtKeyProvider เข้าไปตาม constructor ใหม่
    private final JwtService jwt = new JwtService(new TestJwtKeyProvider());

    private static User user(Long id, String email, Role role) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setRole(role);
        return u;
    }

    @Nested
    @DisplayName("generate(subject, claims, exp) + getAllClaims/getSubject/getRoleCode/getUserId")
    class GenerateAndParse {

        @Test
        @DisplayName("ฝัง subject/claims ครบ และอ่านคืนได้ (iat/exp ต้องมี)")
        void generate_and_parse_ok() {
            String sub = "alice@example.com";
            Map<String, Object> claims = new HashMap<>();
            claims.put(JwtService.CLAIM_ROLE, JwtService.ROLE_ADMIN);
            claims.put(JwtService.CLAIM_UID, 123L);

            String token = jwt.generate(sub, claims, 3600);

            Claims c = jwt.getAllClaims(token);
            assertThat(c.getSubject()).isEqualTo(sub);
            assertThat(c.get(JwtService.CLAIM_ROLE, Integer.class)).isEqualTo(JwtService.ROLE_ADMIN);
            assertThat(c.get(JwtService.CLAIM_UID, Long.class)).isEqualTo(123L);

            assertThat(c.getIssuedAt()).isNotNull();
            assertThat(c.getExpiration()).isNotNull();
            assertThat(c.getExpiration().toInstant()).isAfter(c.getIssuedAt().toInstant());

            // shortcuts
            assertThat(jwt.getSubject(token)).isEqualTo(sub);
            assertThat(jwt.getRoleCode(token)).isEqualTo(JwtService.ROLE_ADMIN);
            assertThat(jwt.getUserId(token)).isEqualTo(123L);
        }

        @Test
        @DisplayName("ไม่มีบางเคลม → getter คืนค่า null")
        void missing_claims_returns_null() {
            String token = jwt.generate("s", Map.of(), 60);
            assertThat(jwt.getRoleCode(token)).isNull();
            assertThat(jwt.getUserId(token)).isNull();
        }

        @Test
        @DisplayName("exp ตั้งอนาคตจากตอนสร้าง (ตรวจหยาบและยืดหยุ่นเรื่องเวลา)")
        void expiration_is_future_from_now() {
            // อนุโลม clock skew/ความละเอียดเวลา 1 วินาที
            Instant lowerBound = Instant.now().minusSeconds(1);

            String token = jwt.generate("s", Map.of(), 2 /* seconds */);
            Claims c = jwt.getAllClaims(token);

            // iat ควร >= lowerBound (ยืดหยุ่น)
            assertThat(c.getIssuedAt()).isNotNull();
            assertThat(c.getIssuedAt().toInstant()).isAfterOrEqualTo(lowerBound);

            // exp > iat เสมอ
            assertThat(c.getExpiration()).isNotNull();
            assertThat(c.getExpiration().toInstant())
                    .isAfter(c.getIssuedAt().toInstant());
        }
    }

    @Nested
    @DisplayName("generateForUser(user, exp)")
    class GenerateForUser {

        @Test
        @DisplayName("role=USER → roleCode=1, subject=email, uid=id")
        void user_role_user() {
            User u = user(5L, "u@x", Role.USER);

            String token = jwt.generateForUser(u, 3600);

            assertThat(jwt.getSubject(token)).isEqualTo("u@x");
            assertThat(jwt.getRoleCode(token)).isEqualTo(JwtService.ROLE_USER);
            assertThat(jwt.getUserId(token)).isEqualTo(5L);
            assertThat(jwt.isAdminToken(token)).isFalse();
        }

        @Test
        @DisplayName("role=ADMIN → roleCode=0 และ isAdminToken=true")
        void user_role_admin() {
            User u = user(99L, "admin@x", Role.ADMIN);

            String token = jwt.generateForUser(u, 3600);

            assertThat(jwt.getRoleCode(token)).isEqualTo(JwtService.ROLE_ADMIN);
            assertThat(jwt.isAdminToken(token)).isTrue();
            assertThat(jwt.getUserId(token)).isEqualTo(99L);
        }

        @Test
        @DisplayName("user ไม่มี role → fallback เป็น USER (1)")
        void user_without_role_defaults_user() {
            User u = user(10L, "no-role@x", null);

            String token = jwt.generateForUser(u, 3600);

            assertThat(jwt.getRoleCode(token)).isEqualTo(JwtService.ROLE_USER);
            assertThat(jwt.getUserId(token)).isEqualTo(10L);
            assertThat(jwt.isAdminToken(token)).isFalse();
        }
    }

    @Nested
    @DisplayName("getUserId(): รองรับ Integer/Long/String และกรณีผิดรูปแบบ")
    class GetUserIdKinds {

        @Test
        @DisplayName("uid เป็น Integer → แปลงเป็น Long")
        void uid_integer() {
            String token = jwt.generate("s", Map.of(JwtService.CLAIM_UID, Integer.valueOf(123)), 100);
            assertThat(jwt.getUserId(token)).isEqualTo(123L);
        }

        @Test
        @DisplayName("uid เป็น Long → คืน Long ตรง ๆ")
        void uid_long() {
            String token = jwt.generate("s", Map.of(JwtService.CLAIM_UID, 9876543210L), 100);
            assertThat(jwt.getUserId(token)).isEqualTo(9876543210L);
        }

        @Test
        @DisplayName("uid เป็น String ตัวเลข → แปลงเป็น Long")
        void uid_string_numeric() {
            String token = jwt.generate("s", Map.of(JwtService.CLAIM_UID, "456"), 100);
            assertThat(jwt.getUserId(token)).isEqualTo(456L);
        }

        @Test
        @DisplayName("uid เป็น String ที่ไม่ใช่ตัวเลข → ควรโยน NumberFormatException")
        void uid_string_non_numeric_throws() {
            String token = jwt.generate("s", Map.of(JwtService.CLAIM_UID, "xyz"), 100);
            assertThatThrownBy(() -> jwt.getUserId(token))
                    .isInstanceOf(NumberFormatException.class);
        }

        @Test
        @DisplayName("ไม่มี uid → คืน null")
        void uid_missing_returns_null() {
            String token = jwt.generate("s", Map.of(JwtService.CLAIM_ROLE, JwtService.ROLE_USER), 100);
            assertThat(jwt.getUserId(token)).isNull();
        }
    }

    @Nested
    @DisplayName("isAdminToken()")
    class IsAdminToken {

        @Test
        @DisplayName("role=ADMIN → true")
        void admin_true() {
            String token = jwt.generate("s", Map.of(JwtService.CLAIM_ROLE, JwtService.ROLE_ADMIN), 100);
            assertThat(jwt.isAdminToken(token)).isTrue();
        }

        @Test
        @DisplayName("role=USER → false")
        void user_false() {
            String token = jwt.generate("s", Map.of(JwtService.CLAIM_ROLE, JwtService.ROLE_USER), 100);
            assertThat(jwt.isAdminToken(token)).isFalse();
        }

        @Test
        @DisplayName("ไม่มี role → false")
        void no_role_false() {
            String token = jwt.generate("s", Map.of(), 100);
            assertThat(jwt.isAdminToken(token)).isFalse();
        }

        @Test
        @DisplayName("role=null ในเคลม → false")
        void role_null_claim_false() {
            Map<String, Object> claims = new HashMap<>();
            claims.put(JwtService.CLAIM_ROLE, null);
            String token = jwt.generate("s", claims, 100);

            assertThat(jwt.isAdminToken(token)).isFalse();
        }
    }

    @Nested
    @DisplayName("โทเคนหมดอายุ/โทเคนพัง")
    class InvalidTokens {

        @Test
        @DisplayName("โทเคนหมดอายุ → getAllClaims() ควรโยน ExpiredJwtException")
        void expired_token_throws() throws InterruptedException {
            String token = jwt.generate("s", Map.of(), 0); // exp = now
            Thread.sleep(5); // เผื่อ clock resolution
            assertThatThrownBy(() -> jwt.getAllClaims(token))
                    .isInstanceOf(ExpiredJwtException.class);
        }

        @Test
        @DisplayName("โทเคนพัง/ไม่ใช่รูปแบบ JWS → getAllClaims() ควรโยน MalformedJwtException")
        void malformed_token_throws() {
            String bad = "this.is.not.jwt";
            assertThatThrownBy(() -> jwt.getAllClaims(bad))
                    .isInstanceOf(MalformedJwtException.class);
        }
    }
}
