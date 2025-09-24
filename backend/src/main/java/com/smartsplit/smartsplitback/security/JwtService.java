package com.smartsplit.smartsplitback.security;

import com.smartsplit.smartsplitback.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class JwtService {

    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_UID  = "uid";
    public static final int ROLE_ADMIN = 0;
    public static final int ROLE_USER  = 1;

    private final JwtKeyProvider keyProvider;

    public JwtService(JwtKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    public String generate(String subject, Map<String, Object> claims, long expiresSeconds) {
        Instant now = Instant.now();
        SecretKey key = keyProvider.getHmacKey();
        return Jwts.builder()
                .subject(subject)
                .claims(claims != null ? claims : Map.of())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expiresSeconds)))
                .signWith(key) // HS256 โดยอัตโนมัติเมื่อเป็น HMAC key
                .compact();
    }

    public String generateForUser(User user, long expiresSeconds) {
        Map<String, Object> claims = new HashMap<>();

        int roleCode = ROLE_USER;
        try {
            var role = user.getRole();
            if (role != null) {
                try {
                    var m = role.getClass().getMethod("code");
                    roleCode = (int) m.invoke(role);
                } catch (Exception ignore) { /* keep default */ }
            }
        } catch (Exception ignore) { /* keep default */ }

        claims.put(CLAIM_ROLE, roleCode);
        claims.put(CLAIM_UID,  user.getId());

        // ใช้ email เป็น subject ตามของเดิม
        return generate(user.getEmail(), claims, expiresSeconds);
    }

    // ===== Parser helpers =====
    public Claims getAllClaims(String token) {
        SecretKey key = keyProvider.getHmacKey();
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getSubject(String token) {
        return getAllClaims(token).getSubject();
    }

    public Integer getRoleCode(String token) {
        return getAllClaims(token).get(CLAIM_ROLE, Integer.class);
    }

    public Long getUserId(String token) {
        Object v = getAllClaims(token).get(CLAIM_UID);
        if (v instanceof Integer) return ((Integer) v).longValue();
        if (v instanceof Long)    return (Long) v;
        if (v instanceof String)  return Long.parseLong((String) v);
        return null;
    }

    public boolean isAdminToken(String token) {
        Integer code = getRoleCode(token);
        return code != null && code == ROLE_ADMIN;
    }
}
