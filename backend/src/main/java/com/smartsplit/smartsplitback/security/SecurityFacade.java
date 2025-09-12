package com.smartsplit.smartsplitback.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;

@Component
public class SecurityFacade {

    private final JwtService jwtService;

    public SecurityFacade(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    public Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;

        Object principal = auth.getPrincipal();
        if (principal instanceof String s) {

            if (s.chars().allMatch(Character::isDigit)) {
                try { return Long.parseLong(s); } catch (NumberFormatException ignore) {}
            }
        }

        // fallback: ยังมี token เก่า → อ่าน uid จาก JWT claim
        String token = resolveBearerToken();
        if (token != null) {
            Long uid = jwtService.getUserId(token);
            if (uid != null) return uid;
        }
        return null;
    }

    public boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        for (GrantedAuthority ga : auth.getAuthorities()) {
            if (Objects.equals(ga.getAuthority(), role)) return true;
        }
        return false;
    }

    public boolean isAdmin() { return hasRole("ROLE_ADMIN"); }

    private String resolveBearerToken() {
        var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return null;
        HttpServletRequest req = attrs.getRequest();
        String h = req.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) return h.substring(7).trim();
        return null;
    }
}
