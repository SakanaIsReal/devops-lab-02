package com.smartsplit.smartsplitback.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "expense_item_shares",
        indexes = {
                @Index(name = "idx_item_shares_item", columnList = "expense_item_id"),
                @Index(name = "idx_item_shares_participant", columnList = "participant_user_id")
        })
public class ExpenseItemShare {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "share_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expense_item_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_item_share_item"))
    private ExpenseItem expenseItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_item_share_participant"))
    private User participant;

    // สามารถใช้แบบจำนวนเงิน หรือแบบเปอร์เซ็นต์ (อย่างใดอย่างหนึ่ง หรือทั้งคู่ถ้าธุรกิจอนุญาต)
    @Column(name = "share_value", precision = 19, scale = 2)
    private BigDecimal shareValue;

    @Column(name = "share_percent", precision = 5, scale = 2)
    private BigDecimal sharePercent;

    // getters/setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ExpenseItem getExpenseItem() { return expenseItem; }
    public void setExpenseItem(ExpenseItem expenseItem) { this.expenseItem = expenseItem; }

    public User getParticipant() { return participant; }
    public void setParticipant(User participant) { this.participant = participant; }

    public BigDecimal getShareValue() { return shareValue; }
    public void setShareValue(BigDecimal shareValue) { this.shareValue = shareValue; }

    public BigDecimal getSharePercent() { return sharePercent; }
    public void setSharePercent(BigDecimal sharePercent) { this.sharePercent = sharePercent; }

    @Override public boolean equals(Object o){ return o instanceof ExpenseItemShare s && Objects.equals(id, s.id); }
    @Override public int hashCode(){ return Objects.hashCode(id); }
}
