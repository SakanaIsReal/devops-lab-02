package com.smartsplit.smartsplitback.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        // ถ้ามี authentication อยู่แล้ว ข้าม
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(req, res);
            return;
        }

        // ต้องมี Bearer token
        final String header = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(req, res);
            return;
        }

        final String token = header.substring(7);
        try {
            // 1) ดึง uid จาก claim (มาตรฐานหลัก)
            Long uid = null;
            try { uid = jwtService.getUserId(token); } catch (Throwable ignore) {}

            // 2) เผื่อ token เก่าที่ยังไม่มี uid → ลอง parse subject เป็นตัวเลข
            if (uid == null) {
                try {
                    String sub = jwtService.getSubject(token);
                    uid = (sub != null && sub.chars().allMatch(Character::isDigit)) ? Long.valueOf(sub) : null;
                } catch (Throwable ignore) {}
            }

            if (uid != null) {
                // role จาก claim (ไม่มี → default USER)
                Integer roleCode = null;
                try { roleCode = jwtService.getRoleCode(token); } catch (Throwable ignore) {}
                String springRole = (roleCode != null && roleCode == JwtService.ROLE_ADMIN) ? "ROLE_ADMIN" : "ROLE_USER";

                // principal = uid (String) เสมอ
                String principal = String.valueOf(uid);

                var authorities = List.of(new SimpleGrantedAuthority(springRole));
                var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        } catch (Exception ignore) {
            // ไม่ throw ต่อ เพื่อไม่ให้ request พังทั้งก้อน
        }

        chain.doFilter(req, res);
    }
}
