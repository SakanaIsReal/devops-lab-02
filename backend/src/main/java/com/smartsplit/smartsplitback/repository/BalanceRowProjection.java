package com.smartsplit.smartsplitback.repository;

import java.math.BigDecimal;

public interface BalanceRowProjection {
    String getDirection();
    Long getGroupId();
    String getGroupName();
    Long getExpenseId();
    String getExpenseTitle();
    Long getCounterpartyUserId();
    String getCounterpartyUserName();
    String getCounterpartyAvatarUrl();
    BigDecimal getRemaining();
}
