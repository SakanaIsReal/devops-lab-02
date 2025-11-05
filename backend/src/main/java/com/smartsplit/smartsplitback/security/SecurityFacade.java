package com.smartsplit.smartsplitback.security;

import com.smartsplit.smartsplitback.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Objects;

@Component
public class SecurityFacade {

    private final JwtService jwtService;
    private final UserRepository userRepo;

    public SecurityFacade(JwtService jwtService, UserRepository userRepo) {
        this.jwtService = jwtService;
        this.userRepo = userRepo;
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

        String token = resolveBearerToken();
        if (token != null) {
            Long uid = jwtService.getUserId(token);
            if (uid != null) return uid;
        }
        return null;
    }

    public boolean hasRole(String role) {
        Long uid = currentUserId();
        if (uid == null) return false;

        var userOpt = userRepo.findById(uid);
        if (userOpt.isEmpty() || userOpt.get().getRole() == null) return false;

        String springRole = "ROLE_" + userOpt.get().getRole().name();
        return springRole.equals(role);
    }


    public boolean isAdmin() {
        return hasRole("ROLE_ADMIN");
    }

    private String resolveBearerToken() {
        var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return null;
        HttpServletRequest req = attrs.getRequest();
        String h = req.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) return h.substring(7).trim();
        return null;
    }
}
