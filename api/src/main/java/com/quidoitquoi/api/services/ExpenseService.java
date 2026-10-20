package com.quidoitquoi.api.services;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.quidoitquoi.api.models.Expense;

public interface ExpenseService {
    List<Expense> getExpensesByGroupId(UUID groupId, UUID authenticatedUserId);

    Optional<Expense> getExpenseById(UUID id, UUID authenticatedUserId);

    Expense addExpense(UUID groupId, Expense expense, UUID madeByUserId);

    Optional<Expense> updateExpense(UUID id, Expense expense, UUID authenticatedUserId);

    boolean deleteExpense(UUID id, UUID authenticatedUserId);
}
