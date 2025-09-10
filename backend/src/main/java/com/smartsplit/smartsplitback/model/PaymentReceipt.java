package com.smartsplit.smartsplitback.model;

import jakarta.persistence.*;
import java.util.Objects;

@Entity
@Table(name = "payment_receipts",
        indexes = {
                @Index(name = "idx_receipts_payment", columnList = "payment_id")
        })
public class PaymentReceipt {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "receipt_id")
    private Long id;

    // one-to-one (owner side)
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "fk_receipt_payment"))
    private ExpensePayment payment;

    @Column(name = "file_url", length = 1000, nullable = false)
    private String fileUrl;

    // getters/setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ExpensePayment getPayment() { return payment; }
    public void setPayment(ExpensePayment payment) { this.payment = payment; }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    @Override public boolean equals(Object o){ return o instanceof PaymentReceipt r && Objects.equals(id, r.id); }
    @Override public int hashCode(){ return Objects.hashCode(id); }
    public void setExpensePayment(ExpensePayment expensePayment) { this.payment = expensePayment; }
}
