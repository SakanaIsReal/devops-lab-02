package com.smartsplit.smartsplitback.repository;

import com.smartsplit.smartsplitback.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByGroup_Id(Long groupId);
    List<Expense> findByPayer_Id(Long userId);


    @Query("select e.group.id from Expense e where e.id = :expenseId")
    Long findGroupIdByExpenseId(@Param("expenseId") Long expenseId);


    @Query("select e.payer.id from Expense e where e.id = :expenseId")
    Long findPayerUserIdByExpenseId(@Param("expenseId") Long expenseId);

    @Query("select i.expense.id from ExpenseItem i where i.id = :itemId")
    Long findExpenseIdByItemId(@Param("itemId") Long itemId);

    @Query("select i.expense.group.id from ExpenseItem i where i.id = :itemId")
    Long findGroupIdByItemId(@Param("itemId") Long itemId);
}
