package com.smartsplit.smartsplitback.repository;

import com.smartsplit.smartsplitback.model.ExpensePayment;
import com.smartsplit.smartsplitback.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.math.BigDecimal;
import java.util.List;

public interface ExpensePaymentRepository extends JpaRepository<ExpensePayment, Long> {

    List<ExpensePayment> findByExpense_Id(Long expenseId);

    @Query("select coalesce(sum(p.amount), 0) " +
            "from ExpensePayment p " +
            "where p.expense.id = :expenseId and p.status = com.smartsplit.smartsplitback.model.PaymentStatus.VERIFIED")
    BigDecimal sumVerifiedAmountByExpenseId(@Param("expenseId") Long expenseId);

    long countByExpense_IdAndStatus(Long expenseId, PaymentStatus status);
    @Query("""
           select coalesce(sum(p.amount), 0)
           from ExpensePayment p
           where p.expense.id = :expenseId
             and p.fromUser.id = :userId
             and p.status = com.smartsplit.smartsplitback.model.PaymentStatus.VERIFIED
           """)
    BigDecimal sumVerifiedAmountByExpenseIdAndUser(@Param("expenseId") Long expenseId,
                                                   @Param("userId") Long userId);

    // รายชื่อผู้ที่มีการจ่าย VERIFIED ใน expense (เอาไว้ union กับคนที่มี share)
    @Query("""
           select distinct p.fromUser.id
           from ExpensePayment p
           where p.expense.id = :expenseId
             and p.status = com.smartsplit.smartsplitback.model.PaymentStatus.VERIFIED
           """)
    List<Long> findVerifiedPayerIdsByExpense(@Param("expenseId") Long expenseId);
    Optional<ExpensePayment> findByIdAndExpense_Id(Long paymentId, Long expenseId);
    boolean existsByIdAndExpense_Id(Long paymentId, Long expenseId);


}
