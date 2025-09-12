package com.smartsplit.smartsplitback.repository;

import com.smartsplit.smartsplitback.model.ExpenseItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.math.BigDecimal;
import java.util.List;

public interface ExpenseItemRepository extends JpaRepository<ExpenseItem, Long> {

    List<ExpenseItem> findByExpense_Id(Long expenseId);

    @Query("select coalesce(sum(i.amount), 0) " +
            "from ExpenseItem i where i.expense.id = :expenseId")
    BigDecimal sumAmountByExpenseId(@Param("expenseId") Long expenseId);
    @Query("select i.expense.id from ExpenseItem i where i.id = :itemId")
    Long findExpenseIdByItemId(@Param("itemId") Long itemId);
    Optional<ExpenseItem> findByIdAndExpense_Id(Long itemId, Long expenseId);
    boolean existsByIdAndExpense_Id(Long itemId, Long expenseId);
}
