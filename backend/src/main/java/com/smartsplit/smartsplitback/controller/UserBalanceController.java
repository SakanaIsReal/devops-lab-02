package com.smartsplit.smartsplitback.controller;

import com.smartsplit.smartsplitback.model.dto.BalanceLineDto;
import com.smartsplit.smartsplitback.model.dto.BalanceSummaryDto;
import com.smartsplit.smartsplitback.service.UserBalanceService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.util.List;

@RestController
@RequestMapping({"/api/me/balances"})
public class UserBalanceController {

    private final UserBalanceService svc;

    public UserBalanceController(UserBalanceService svc) {
        this.svc = svc;
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping({""})
    public List<BalanceLineDto> list(Authentication auth) {
        Long userId = extractUserId(auth);
        return svc.listBalances(userId);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/summary")
    public BalanceSummaryDto summary(Authentication auth) {
        Long userId = extractUserId(auth);
        return svc.summary(userId);
    }

    private Long extractUserId(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No authentication");
        }
        Object p = auth.getPrincipal();

        // ลองเรียกเมธอดยอดฮิตที่โปรเจกต์มักใช้เก็บ user id
        for (String m : new String[]{"getId", "getUid", "getUserId"}) {
            try {
                Method md = p.getClass().getMethod(m);
                Object v = md.invoke(p);
                if (v != null) return Long.valueOf(String.valueOf(v));
            } catch (Exception ignored) {}
        }

        // สำรอง: ถ้า subject ใน token เป็น userId ตรง ๆ
        try {
            return Long.valueOf(auth.getName());
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Cannot resolve user id from token (principal=" + p.getClass().getSimpleName() + ")"
            );
        }
    }
}
