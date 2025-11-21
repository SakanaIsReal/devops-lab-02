package com.smartsplit.smartsplitback.service;

import com.smartsplit.smartsplitback.model.Expense;
import com.smartsplit.smartsplitback.repository.ExpenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smartsplit.smartsplitback.repository.ExpenseItemShareRepository;
import java.util.List;

@Service
@Transactional
public class ExpenseService {
    private final ExpenseRepository repo;
    private final ExpenseItemShareRepository shareRepo;
    public ExpenseService(ExpenseRepository repo,ExpenseItemShareRepository shareRepo){
        this.repo = repo;
        this.shareRepo = shareRepo;
    }

    @Transactional(readOnly = true)
    public List<Expense> listByParticipant(Long userId) {
        var ids = shareRepo.findDistinctExpenseIdsByParticipantId(userId);
        if (ids == null || ids.isEmpty()) return List.of();
        return repo.findAllById(ids);
    }

    public List<Expense> list(){ return repo.findAll(); }
    public List<Expense> listByGroup(Long groupId){ return repo.findByGroup_Id(groupId); }
    public List<Expense> listByPayer(Long userId){ return repo.findByPayer_Id(userId); }
    public Expense get(Long id){ return repo.findById(id).orElse(null); }
    public Expense save(Expense e){ return repo.save(e); }
    public void delete(Long id){ repo.deleteById(id); }
}
