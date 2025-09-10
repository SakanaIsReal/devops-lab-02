package com.smartsplit.smartsplitback.model.dto;

import java.math.BigDecimal;

public record BalanceSummaryDto(
        BigDecimal youOweTotal,
        BigDecimal youAreOwedTotal
) {}
