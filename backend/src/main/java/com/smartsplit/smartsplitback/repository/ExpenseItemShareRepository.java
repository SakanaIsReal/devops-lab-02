package com.smartsplit.smartsplitback.repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.smartsplit.smartsplitback.model.ExpenseItemShare;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface ExpenseItemShareRepository extends JpaRepository<ExpenseItemShare, Long> {

    List<ExpenseItemShare> findByExpenseItem_Id(Long expenseItemId);


    List<ExpenseItemShare> findByExpenseItem_Expense_Id(Long expenseId);
    // ดึง shares ของ user ใน expense พร้อม join fetch expenseItem เพื่อให้มี item.amount
    @Query("""
           select s from ExpenseItemShare s
           join fetch s.expenseItem i
           where i.expense.id = :expenseId and s.participant.id = :userId
           """)
    List<ExpenseItemShare> fetchForExpenseAndUser(@Param("expenseId") Long expenseId,
                                                  @Param("userId") Long userId);

    // ดึง shares ทั้ง expense (พร้อม item)
    @Query("""
           select s from ExpenseItemShare s
           join fetch s.expenseItem i
           where i.expense.id = :expenseId
           """)
    List<ExpenseItemShare> fetchForExpense(@Param("expenseId") Long expenseId);

    List<ExpenseItemShare> findByExpenseItem_Expense_IdAndParticipant_Id(Long expenseId, Long userId);

    // รายชื่อผู้มี share ใน expense นี้
    @Query("""
           select distinct s.participant.id
           from ExpenseItemShare s
           where s.expenseItem.expense.id = :expenseId
           """)
    List<Long> findParticipantIdsByExpense(@Param("expenseId") Long expenseId);
    boolean existsByExpenseItem_Expense_IdAndParticipant_Id(Long expenseId, Long userId);

    Optional<ExpenseItemShare> findByIdAndExpenseItem_IdAndExpenseItem_Expense_Id(Long shareId, Long itemId, Long expenseId);
    boolean existsByIdAndExpenseItem_IdAndExpenseItem_Expense_Id(Long shareId, Long itemId, Long expenseId);

}
