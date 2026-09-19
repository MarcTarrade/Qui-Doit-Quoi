package com.quidoitquoi.api.services;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.quidoitquoi.api.models.Expense;

public interface ExpenseService {
    List<Expense> getExpensesByGroupId(UUID groupId);

    Optional<Expense> getExpenseById(UUID id);

    Expense addExpense(UUID groupId, Expense expense);

    Optional<Expense> updateExpense(UUID id, Expense expense);

    boolean deleteExpense(UUID id);
}
