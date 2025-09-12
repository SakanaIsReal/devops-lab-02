package com.smartsplit.smartsplitback.security;

import com.smartsplit.smartsplitback.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class JwtService {


    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_UID  = "uid";
    public static final int ROLE_ADMIN = 0;
    public static final int ROLE_USER  = 1;


    private static final String SECRET_B64 =
            "f9wvJfbA1AZQeGlc1x3B8joQcXokci8Z/k57Q4Evu7/d7pqnuKmiyjqGFO9Rkjr7vmghxbV+Ob6vR3k0f/7eU7A7uhwYW18489kmUU14OJYuIk/EJ9s8A3p5hhCUZS7BAAq/nDj2GvabgbXCP+PWmkzEZw96OnkRUwDw90dlA5Q0Pw/xjgNyhELSprXPJD6NjPu9cSSEALSFrB7lHZDQYtLcenYwo38YLNFCc8Ppp0/U9SWm513HDynszLAkg5bQD/S8KjpkNiC16wnosp15RMVFG0LlWekuo4KZYTC4CKe26b5+BWqtLYXrMqPn4y3Ln+iOKmV2Imc0M1bOfKf4iYJ3k0ubhw2ew8teJBjpHt0=";

    private final SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET_B64));


    public String generate(String subject, Map<String, Object> claims, long expiresSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expiresSeconds)))
                .signWith(key)
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
                } catch (Exception ignore) {

                }
            }
        } catch (Exception ignore) {}

        claims.put(CLAIM_ROLE, roleCode);
        claims.put(CLAIM_UID, user.getId());

        return generate(user.getEmail(), claims, expiresSeconds);
    }

    // ===== Parser helpers =====
    public Claims getAllClaims(String token) {
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
