
package com.smartsplit.smartsplitback.controller;

import com.smartsplit.smartsplitback.model.dto.BalanceLineDto;
import com.smartsplit.smartsplitback.model.dto.BalanceSummaryDto;
import com.smartsplit.smartsplitback.security.Perms;
import com.smartsplit.smartsplitback.service.UserBalanceService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/me/balances")
public class UserBalanceController {

    private final UserBalanceService svc;
    private final Perms perm;

    public UserBalanceController(UserBalanceService svc, Perms perm) {
        this.svc = svc;
        this.perm = perm;
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public List<BalanceLineDto> list() {
        Long myId = perm.currentUserId(); // ดึงจาก token
        return svc.listBalances(myId);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/summary")
    public BalanceSummaryDto summary() {
        Long myId = perm.currentUserId(); // ดึงจาก token
        return svc.summary(myId);
    }
}
