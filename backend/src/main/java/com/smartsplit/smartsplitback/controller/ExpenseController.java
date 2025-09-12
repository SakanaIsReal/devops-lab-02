package com.smartsplit.smartsplitback.controller;

import com.smartsplit.smartsplitback.model.*;
import com.smartsplit.smartsplitback.model.dto.ExpenseDto;
import com.smartsplit.smartsplitback.model.dto.ExpenseItemDto;
import com.smartsplit.smartsplitback.service.ExpenseService;
import com.smartsplit.smartsplitback.service.GroupService;
import com.smartsplit.smartsplitback.service.UserService;
import com.smartsplit.smartsplitback.service.ExpenseItemService;
import com.smartsplit.smartsplitback.service.ExpensePaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.smartsplit.smartsplitback.model.dto.ExpenseSettlementDto;
import com.smartsplit.smartsplitback.service.ExpenseSettlementService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final ExpenseService expenses;
    private final GroupService groups;
    private final UserService users;
    private final ExpenseSettlementService settlementService;

    private final ExpenseItemService itemService;
    private final ExpensePaymentService paymentService;

    public ExpenseController(ExpenseService expenses,
                             GroupService groups,
                             UserService users,
                             ExpenseItemService itemService,
                             ExpensePaymentService paymentService,
                             ExpenseSettlementService settlementService) {
        this.expenses = expenses;
        this.groups = groups;
        this.users = users;
        this.itemService = itemService;
        this.paymentService = paymentService;
        this.settlementService = settlementService;
    }


    @PreAuthorize("@perm.isAdmin()")
    @GetMapping
    public List<ExpenseDto> list(@RequestParam(required = false) Long groupId,
                                 @RequestParam(required = false) Long payerUserId){
        var list = groupId!=null ? expenses.listByGroup(groupId)
                : payerUserId!=null ? expenses.listByPayer(payerUserId)
                : expenses.list();
        return list.stream().map(ExpenseController::toDto).toList();
    }


    @PreAuthorize("@perm.canViewExpense(#id)")
    @GetMapping("/{id}")
    public ExpenseDto get(@PathVariable Long id){
        var e = expenses.get(id);
        if(e==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Expense not found");
        return toDto(e);
    }


    @PreAuthorize("@perm.isGroupMember(#groupId)")
    @GetMapping("/group/{groupId}")
    public List<ExpenseDto> listByGroupForMember(@PathVariable Long groupId) {
        return expenses.listByGroup(groupId).stream()
                .map(ExpenseController::toDto)
                .toList();
    }


    @PreAuthorize("@perm.canCreateExpenseInGroup(#in.groupId())")
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ExpenseDto create(@RequestBody ExpenseDto in){
        if(in.groupId()==null || in.payerUserId()==null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"groupId and payerUserId are required");

        Group g = groups.get(in.groupId());
        if(g==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Group not found");
        User p = users.get(in.payerUserId());
        if(p==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Payer user not found");

        Expense e = new Expense();
        e.setGroup(g);
        e.setPayer(p);
        e.setAmount(in.amount());
        e.setType(in.type());
        e.setTitle(in.title());
        e.setStatus(in.status());
        return toDto(expenses.save(e));
    }


    @PreAuthorize("@perm.canManageExpense(#id)")
    @PutMapping("/{id}")
    public ExpenseDto update(@PathVariable Long id, @RequestBody ExpenseDto in){
        Expense e = expenses.get(id);
        if(e==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Expense not found");

        if(in.groupId()!=null){
            Group g = groups.get(in.groupId());
            if(g==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Group not found");
            e.setGroup(g);
        }
        if(in.payerUserId()!=null){
            User p = users.get(in.payerUserId());
            if(p==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Payer user not found");
            e.setPayer(p);
        }
        if(in.amount()!=null) e.setAmount(in.amount());
        if(in.type()!=null) e.setType(in.type());
        if(in.title()!=null) e.setTitle(in.title());
        if(in.status()!=null) e.setStatus(in.status());

        return toDto(expenses.save(e));
    }


    @PreAuthorize("@perm.canManageExpense(#id)")
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id){
        if(expenses.get(id)==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Expense not found");
        expenses.delete(id);

    }

    @PreAuthorize("@perm.canViewExpense(#id)")
    @GetMapping("/{id}/total-items")
    public BigDecimal itemsTotal(@PathVariable Long id) {
        return itemService.sumItems(id);
    }


    @PreAuthorize("@perm.canViewExpense(#id)")
    @GetMapping("/{id}/total-verified")
    public BigDecimal verifiedTotal(@PathVariable Long id) {
        return paymentService.sumVerified(id);
    }


    @PreAuthorize("@perm.canViewExpense(#id)")
    @GetMapping("/{id}/summary")
    public Map<String, BigDecimal> summary(@PathVariable Long id) {
        return Map.of(
                "itemsTotal", itemService.sumItems(id),
                "verifiedTotal", paymentService.sumVerified(id)
        );
    }

    @PreAuthorize("@perm.canViewExpense(#id)")
    @GetMapping("/{id}/settlement/{userId}")
    public ExpenseSettlementDto settlementByUser(@PathVariable Long id,
                                                 @PathVariable Long userId) {
        return settlementService.userSettlement(id, userId);
    }


    @PreAuthorize("@perm.canViewExpense(#id)")
    @GetMapping("/{id}/settlement")
    public List<ExpenseSettlementDto> settlementAll(@PathVariable Long id) {
        return settlementService.allSettlements(id);
    }

    private static ExpenseDto toDto(Expense e){
        return new ExpenseDto(
                e.getId(),
                e.getGroup().getId(),
                e.getPayer().getId(),
                e.getAmount(),
                e.getType(),
                e.getTitle(),
                e.getStatus(),
                e.getCreatedAt()
        );
    }
}
