package com.smartsplit.smartsplitback.model;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "expenses",
        indexes = {
                @Index(name="idx_expenses_group", columnList="group_id"),
                @Index(name="idx_expenses_payer", columnList="payer_user_id")
        })
public class Expense {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "expense_id")
    private Long id;


    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_expenses_group"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payer_user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_expenses_payer"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User payer;

    @Column(precision = 18, scale = 2, nullable = false)
    private BigDecimal amount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING) @Column(length=20, nullable=false)
    private ExpenseType type = ExpenseType.EQUAL;

    @Column(length = 200, nullable = false)
    private String title;

    @Enumerated(EnumType.STRING) @Column(length=20, nullable=false)
    private ExpenseStatus status = ExpenseStatus.OPEN;

    @Column(name="created_at", nullable=false)
    private LocalDateTime createdAt;

    @PrePersist void onCreate(){ if(createdAt==null) createdAt=LocalDateTime.now(); }




    @OneToMany(mappedBy = "expense", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExpenseItem> items = new ArrayList<>();


    @OneToMany(mappedBy = "expense", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExpensePayment> payments = new ArrayList<>();


    public void addItem(ExpenseItem item){
        items.add(item);
        item.setExpense(this);
    }
    public void removeItem(ExpenseItem item){
        items.remove(item);
        item.setExpense(null);
    }

    public void addPayment(ExpensePayment p){
        payments.add(p);
        p.setExpense(this);
    }
    public void removePayment(ExpensePayment p){
        payments.remove(p);
        p.setExpense(null);
    }



    @Transient
    public BigDecimal getItemsTotal(){
        BigDecimal sum = BigDecimal.ZERO;
        for (ExpenseItem it : items){
            if (it != null && it.getAmount() != null) {
                sum = sum.add(it.getAmount());
            }
        }
        return sum;
    }

    @Transient
    public BigDecimal getVerifiedPaymentsTotal(){
        BigDecimal sum = BigDecimal.ZERO;
        for (ExpensePayment p : payments){
            if (p != null && p.getAmount() != null && p.getStatus() == PaymentStatus.VERIFIED){
                sum = sum.add(p.getAmount());
            }
        }
        return sum;
    }

    /** (3) ดึง expense_items ทั้งหมดของ expense นี้ */
    @Transient
    public List<ExpenseItem> getExpenseItems(){
        return items;
    }

    /* -------------------- getters/setters/equals/hash -------------------- */

    public Long getId(){ return id; } public void setId(Long id){ this.id=id; }
    public Group getGroup(){ return group; } public void setGroup(Group group){ this.group=group; }
    public User getPayer(){ return payer; } public void setPayer(User payer){ this.payer=payer; }
    public BigDecimal getAmount(){ return amount; } public void setAmount(BigDecimal amount){ this.amount=amount; }
    public ExpenseType getType(){ return type; } public void setType(ExpenseType type){ this.type=type; }
    public String getTitle(){ return title; } public void setTitle(String title){ this.title=title; }
    public ExpenseStatus getStatus(){ return status; } public void setStatus(ExpenseStatus status){ this.status=status; }
    public LocalDateTime getCreatedAt(){ return createdAt; } public void setCreatedAt(LocalDateTime createdAt){ this.createdAt=createdAt; }

    public List<ExpenseItem> getItems() { return items; }
    public void setItems(List<ExpenseItem> items) { this.items = items; }
    public List<ExpensePayment> getPayments() { return payments; }
    public void setPayments(List<ExpensePayment> payments) { this.payments = payments; }

    @Override public boolean equals(Object o){ return o instanceof Expense e && Objects.equals(id,e.id); }
    @Override public int hashCode(){ return Objects.hashCode(id); }
}
