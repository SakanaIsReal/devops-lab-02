package com.smartsplit.smartsplitback.service;

import com.smartsplit.smartsplitback.model.dto.BalanceLineDto;
import com.smartsplit.smartsplitback.model.dto.BalanceSummaryDto;
import com.smartsplit.smartsplitback.repository.BalanceQueryRepository;
import com.smartsplit.smartsplitback.repository.BalanceRowProjection;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class UserBalanceService {

    private final BalanceQueryRepository repo;

    public UserBalanceService(BalanceQueryRepository repo) {
        this.repo = repo;
    }

    public List<BalanceLineDto> listBalances(Long userId) {
        return repo.findBalancesForUser(userId).stream()
                .map(UserBalanceService::toDto)
                .toList();
    }

    public BalanceSummaryDto summary(Long userId) {
        var rows = repo.findBalancesForUser(userId);
        var youOwe = rows.stream().filter(r -> "YOU_OWE".equals(r.getDirection()))
                .map(BalanceRowProjection::getRemaining).reduce(BigDecimal.ZERO, BigDecimal::add);
        var youAreOwed = rows.stream().filter(r -> "OWES_YOU".equals(r.getDirection()))
                .map(BalanceRowProjection::getRemaining).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new BalanceSummaryDto(youOwe, youAreOwed);
    }

    private static BalanceLineDto toDto(BalanceRowProjection p) {
        return new BalanceLineDto(
                p.getDirection(),
                p.getCounterpartyUserId(),
                p.getCounterpartyUserName(),
                p.getCounterpartyAvatarUrl(),
                p.getGroupId(),
                p.getGroupName(),
                p.getExpenseId(),
                p.getExpenseTitle(),
                p.getRemaining()
        );
    }
}
