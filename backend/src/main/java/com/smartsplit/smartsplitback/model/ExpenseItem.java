package com.smartsplit.smartsplitback.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "expense_items",
        indexes = {
                @Index(name = "idx_expense_items_expense", columnList = "expense_id")
        })
public class ExpenseItem {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "expense_item_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expense_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_expense_items_expense"))
    private Expense expense;

    @Column(length = 200, nullable = false)
    private String name;

    @Column(precision = 19, scale = 2, nullable = false)
    private BigDecimal amount;

    // shares ของรายการนี้
    @OneToMany(mappedBy = "expenseItem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExpenseItemShare> shares = new ArrayList<>();

    // getters/setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Expense getExpense() { return expense; }
    public void setExpense(Expense expense) { this.expense = expense; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public List<ExpenseItemShare> getShares() { return shares; }
    public void setShares(List<ExpenseItemShare> shares) { this.shares = shares; }

    @Override public boolean equals(Object o){ return o instanceof ExpenseItem e && Objects.equals(id, e.id); }
    @Override public int hashCode(){ return Objects.hashCode(id); }
}
