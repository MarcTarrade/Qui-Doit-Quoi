package com.quidoitquoi.api.controllers;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.quidoitquoi.api.exceptions.ExpenseNotFoundException;
import com.quidoitquoi.api.models.Expense;
import com.quidoitquoi.api.services.ExpenseService;

@RestController
@RequestMapping("/api")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @GetMapping("/groups/{groupId}/expenses")
    public ResponseEntity<List<Expense>> getExpensesByGroupId(@PathVariable UUID groupId) {
        return ResponseEntity.ok(expenseService.getExpensesByGroupId(groupId));
    }

    @GetMapping("/expenses/{id}")
    public ResponseEntity<Expense> getExpenseById(@PathVariable UUID id) {
        Expense expense = expenseService.getExpenseById(id)
                .orElseThrow(() -> new ExpenseNotFoundException(id));
        return ResponseEntity.ok(expense);
    }

    @PostMapping("/groups/{groupId}/expenses")
    public ResponseEntity<Expense> addExpense(
            @PathVariable UUID groupId,
            @RequestBody Expense expense) {
        Expense createdExpense = expenseService.addExpense(groupId, expense);
        return ResponseEntity.created(URI.create("/api/expenses/" + createdExpense.getId()))
                .body(createdExpense);
    }

    @PutMapping("/expenses/{id}")
    public ResponseEntity<Expense> updateExpense(
            @PathVariable UUID id,
            @RequestBody Expense expense) {
        Expense updatedExpense = expenseService.updateExpense(id, expense)
                .orElseThrow(() -> new ExpenseNotFoundException(id));
        return ResponseEntity.ok(updatedExpense);
    }

    @DeleteMapping("/expenses/{id}")
    public ResponseEntity<Void> deleteExpense(@PathVariable UUID id) {
        if (!expenseService.deleteExpense(id)) {
            throw new ExpenseNotFoundException(id);
        }
        return ResponseEntity.noContent().build();
    }
}
