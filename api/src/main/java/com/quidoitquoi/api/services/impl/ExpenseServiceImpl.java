package com.quidoitquoi.api.services.impl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.quidoitquoi.api.exceptions.GroupNotFoundException;
import com.quidoitquoi.api.exceptions.MemberNotFoundException;
import com.quidoitquoi.api.exceptions.UserNotFoundException;
import com.quidoitquoi.api.models.Expense;
import com.quidoitquoi.api.models.Group;
import com.quidoitquoi.api.models.GroupLog;
import com.quidoitquoi.api.models.Member;
import com.quidoitquoi.api.models.User;
import com.quidoitquoi.api.repository.ExpenseRepository;
import com.quidoitquoi.api.repository.GroupLogRepository;
import com.quidoitquoi.api.repository.GroupRepository;
import com.quidoitquoi.api.repository.MemberRepository;
import com.quidoitquoi.api.repository.UserRepository;
import com.quidoitquoi.api.services.ExpenseService;

@Service
public class ExpenseServiceImpl implements ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final GroupLogRepository groupLogRepository;
    private final GroupRepository groupRepository;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;

    public ExpenseServiceImpl(
            ExpenseRepository expenseRepository,
            GroupLogRepository groupLogRepository,
            GroupRepository groupRepository,
            MemberRepository memberRepository,
            UserRepository userRepository) {
        this.expenseRepository = expenseRepository;
        this.groupLogRepository = groupLogRepository;
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
    }

    @Override
    public List<Expense> getExpensesByGroupId(UUID groupId) {
        ensureGroupExists(groupId);
        return expenseRepository.findByGroup_Id(groupId);
    }

    @Override
    public Optional<Expense> getExpenseById(UUID id) {
        return expenseRepository.findById(id);
    }

    @Override
    @Transactional
    public Expense addExpense(UUID groupId, Expense expense, UUID madeByUserId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(groupId));
        User madeBy = userRepository.findById(madeByUserId)
                .orElseThrow(() -> new UserNotFoundException(madeByUserId));
        expense.setGroup(group);
        expense.setPaidBy(resolveMember(expense));
        Expense savedExpense = expenseRepository.save(expense);
        groupLogRepository.save(new GroupLog(GroupLog.EXPENSE_ADDED, group, madeBy));
        return savedExpense;
    }

    @Override
    public Optional<Expense> updateExpense(UUID id, Expense expense) {
        return expenseRepository.findById(id).map(existingExpense -> {
            existingExpense.setDescription(expense.getDescription());
            existingExpense.setAmount(expense.getAmount());
            existingExpense.setCategory(expense.getCategory());
            existingExpense.setPaidBy(resolveMember(expense));
            return expenseRepository.save(existingExpense);
        });
    }

    @Override
    public boolean deleteExpense(UUID id) {
        if (!expenseRepository.existsById(id)) {
            return false;
        }

        expenseRepository.deleteById(id);
        return true;
    }

    private void ensureGroupExists(UUID groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new GroupNotFoundException(groupId);
        }
    }

    private Member resolveMember(Expense expense) {
        if (expense.getPaidBy() == null || expense.getPaidBy().getId() == null) {
            return null;
        }

        UUID memberId = expense.getPaidBy().getId();
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException(memberId));
    }
}
