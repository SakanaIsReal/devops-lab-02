package com.smartsplit.smartsplitback.service;

import com.smartsplit.smartsplitback.model.Expense;
import com.smartsplit.smartsplitback.model.ExpenseItem;
import com.smartsplit.smartsplitback.repository.ExpenseItemRepository;
import com.smartsplit.smartsplitback.repository.ExpenseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
public class ExpenseItemService {

    private final ExpenseItemRepository items;
    private final ExpenseRepository expenses;

    public ExpenseItemService(ExpenseItemRepository items, ExpenseRepository expenses) {
        this.items = items;
        this.expenses = expenses;
    }

    @Transactional(readOnly = true)
    public List<ExpenseItem> listByExpense(Long expenseId) {
        return items.findByExpense_Id(expenseId);
    }

    @Transactional(readOnly = true)
    public ExpenseItem getInExpense(Long expenseId, Long itemId) {
        return items.findByIdAndExpense_Id(itemId, expenseId)
                .orElse(null);
    }

    @Transactional
    public ExpenseItem create(Long expenseId, String name, BigDecimal amount, String currency) {
        Expense e = expenses.findById(expenseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found"));

        ExpenseItem item = new ExpenseItem();
        item.setExpense(e);
        item.setName(name);
        item.setAmount(amount);
        item.setCurrency(normalizeCcy(currency));

        return items.save(item);
    }

    @Transactional
    public ExpenseItem updateInExpense(Long expenseId, Long itemId, String name, BigDecimal amount, String currency) {
        ExpenseItem item = items.findByIdAndExpense_Id(itemId, expenseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Expense item not found in this expense"));

        if (name != null)   item.setName(name);
        if (amount != null) item.setAmount(amount);
        if (currency != null && !currency.isBlank()) item.setCurrency(normalizeCcy(currency));

        return items.save(item);
    }

    @Transactional
    public void deleteInExpense(Long expenseId, Long itemId) {
        boolean exists = items.existsByIdAndExpense_Id(itemId, expenseId);
        if (!exists) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense item not found in this expense");
        }
        items.deleteById(itemId);
    }

    @Transactional(readOnly = true)
    public BigDecimal sumItems(Long expenseId) {
        BigDecimal sum = items.sumAmountByExpenseId(expenseId);
        return (sum != null) ? sum : BigDecimal.ZERO;
    }

    @Deprecated @Transactional(readOnly = true)
    public ExpenseItem get(Long id) {
        return items.findById(id).orElse(null);
    }

    @Deprecated @Transactional
    public ExpenseItem update(Long itemId, String name, BigDecimal amount) {
        ExpenseItem item = items.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense item not found"));
        if (name != null)   item.setName(name);
        if (amount != null) item.setAmount(amount);
        return items.save(item);
    }

    @Deprecated @Transactional
    public void delete(Long itemId) {
        if (!items.existsById(itemId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense item not found");
        }
        items.deleteById(itemId);
    }

    private String normalizeCcy(String ccy) {
        String v = (ccy == null || ccy.isBlank()) ? "THB" : ccy.trim().toUpperCase(Locale.ROOT);
        if (v.length() != 3) v = "THB";
        return v;
    }
}
