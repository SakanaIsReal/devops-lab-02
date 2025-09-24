package com.smartsplit.smartsplitback.security;

import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Component
public class JwtKeyProvider {

    @Value("${app.jwt.secret}")
    private String configuredSecret;

    public SecretKey getHmacKey() {
        byte[] keyBytes;
        try {
            // ลอง decode เป็น Base64 ก่อน
            keyBytes = Decoders.BASE64.decode(configuredSecret);
        } catch (IllegalArgumentException notBase64) {
            // ถ้าไม่ใช่ Base64 ใช้เป็น raw bytes
            keyBytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
        }
        return Keys.hmacShaKeyFor(keyBytes); // ต้องยาว >= 32 ไบต์สำหรับ HS256
    }
}
