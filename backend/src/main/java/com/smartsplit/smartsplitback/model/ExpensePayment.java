package com.smartsplit.smartsplitback.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "expense_payments",
        indexes = {
                @Index(name = "idx_payments_expense", columnList = "expense_id"),
                @Index(name = "idx_payments_from_user", columnList = "from_user_id"),
                @Index(name = "idx_payments_status", columnList = "status")
        })
public class ExpensePayment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expense_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_payment_expense"))
    private Expense expense;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_payment_from_user"))
    private User fromUser;

    @Column(precision = 19, scale = 2, nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "verified_at")
    private Instant verifiedAt;

    // one-to-one ไปยังใบเสร็จ (ลบ payment → ลบ receipt)
    @OneToOne(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private PaymentReceipt receipt;

    // getters/setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Expense getExpense() { return expense; }
    public void setExpense(Expense expense) { this.expense = expense; }

    public User getFromUser() { return fromUser; }
    public void setFromUser(User fromUser) { this.fromUser = fromUser; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }

    public PaymentReceipt getReceipt() { return receipt; }
    public void setReceipt(PaymentReceipt receipt) { this.receipt = receipt; }

    @Override public boolean equals(Object o){ return o instanceof ExpensePayment p && Objects.equals(id, p.id); }
    @Override public int hashCode(){ return Objects.hashCode(id); }
}
