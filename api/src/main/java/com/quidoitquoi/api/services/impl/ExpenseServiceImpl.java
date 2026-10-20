package com.quidoitquoi.api.services.impl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.quidoitquoi.api.exceptions.MemberNotFoundException;
import com.quidoitquoi.api.exceptions.ExpenseNotFoundException;
import com.quidoitquoi.api.exceptions.UserNotFoundException;
import com.quidoitquoi.api.models.Expense;
import com.quidoitquoi.api.models.Group;
import com.quidoitquoi.api.models.GroupLog;
import com.quidoitquoi.api.models.Member;
import com.quidoitquoi.api.models.User;
import com.quidoitquoi.api.repository.ExpenseRepository;
import com.quidoitquoi.api.repository.GroupLogRepository;
import com.quidoitquoi.api.repository.MemberRepository;
import com.quidoitquoi.api.repository.UserRepository;
import com.quidoitquoi.api.services.GroupAuthorizationService;
import com.quidoitquoi.api.services.ExpenseService;

@Service
public class ExpenseServiceImpl implements ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final GroupLogRepository groupLogRepository;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final GroupAuthorizationService groupAuthorizationService;

    public ExpenseServiceImpl(
            ExpenseRepository expenseRepository,
            GroupLogRepository groupLogRepository,
            MemberRepository memberRepository,
            UserRepository userRepository,
            GroupAuthorizationService groupAuthorizationService) {
        this.expenseRepository = expenseRepository;
        this.groupLogRepository = groupLogRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.groupAuthorizationService = groupAuthorizationService;
    }

    @Override
    public List<Expense> getExpensesByGroupId(UUID groupId, UUID authenticatedUserId) {
        groupAuthorizationService.requireMember(groupId, authenticatedUserId);
        return expenseRepository.findByGroup_Id(groupId);
    }

    @Override
    public Optional<Expense> getExpenseById(UUID id, UUID authenticatedUserId) {
        return expenseRepository.findById(id).map(expense -> {
            if (!groupAuthorizationService.isMember(expense.getGroup(), authenticatedUserId)) {
                throw new ExpenseNotFoundException(id);
            }
            return expense;
        });
    }

    @Override
    @Transactional
    public Expense addExpense(UUID groupId, Expense expense, UUID madeByUserId) {
        Group group = groupAuthorizationService.requireMember(groupId, madeByUserId);
        User madeBy = userRepository.findById(madeByUserId)
                .orElseThrow(() -> new UserNotFoundException(madeByUserId));
        expense.setGroup(group);
        expense.setPaidBy(resolveMember(expense, groupId));
        Expense savedExpense = expenseRepository.save(expense);
        groupLogRepository.save(new GroupLog(GroupLog.EXPENSE_ADDED, group, madeBy));
        return savedExpense;
    }

    @Override
    public Optional<Expense> updateExpense(UUID id, Expense expense, UUID authenticatedUserId) {
        return expenseRepository.findById(id).map(existingExpense -> {
            UUID groupId = existingExpense.getGroup().getId();
            if (!groupAuthorizationService.isMember(existingExpense.getGroup(), authenticatedUserId)) {
                throw new ExpenseNotFoundException(id);
            }
            existingExpense.setDescription(expense.getDescription());
            existingExpense.setAmount(expense.getAmount());
            existingExpense.setCategory(expense.getCategory());
            existingExpense.setPaidBy(resolveMember(expense, groupId));
            return expenseRepository.save(existingExpense);
        });
    }

    @Override
    public boolean deleteExpense(UUID id, UUID authenticatedUserId) {
        Optional<Expense> expense = expenseRepository.findById(id);
        if (expense.isEmpty()) {
            return false;
        }
        if (!groupAuthorizationService.isMember(expense.get().getGroup(), authenticatedUserId)) {
            throw new ExpenseNotFoundException(id);
        }
        expenseRepository.delete(expense.get());
        return true;
    }

    private Member resolveMember(Expense expense, UUID groupId) {
        if (expense.getPaidBy() == null || expense.getPaidBy().getId() == null) {
            return null;
        }

        UUID memberId = expense.getPaidBy().getId();
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException(memberId));
        if (!member.getGroup().getId().equals(groupId)) {
            throw new MemberNotFoundException(memberId);
        }
        return member;
    }
}
