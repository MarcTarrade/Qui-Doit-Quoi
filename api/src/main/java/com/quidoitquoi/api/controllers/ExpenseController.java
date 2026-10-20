package com.quidoitquoi.api.controllers;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.quidoitquoi.api.exceptions.ExpenseNotFoundException;
import com.quidoitquoi.api.models.Expense;
import com.quidoitquoi.api.services.ExpenseService;

@RestController
@RequestMapping("/api")
@Tag(name = "Expenses", description = "Shared expense management")
@SecurityRequirement(name = "bearerAuth")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @GetMapping("/groups/{groupId}/expenses")
    @Operation(summary = "List group expenses", description = "Returns expenses to an authenticated group member.")
    @ApiResponse(responseCode = "200", description = "Expenses returned")
    public ResponseEntity<List<Expense>> getExpensesByGroupId(
            @PathVariable UUID groupId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(expenseService.getExpensesByGroupId(groupId, authenticatedUserId(jwt)));
    }

    @GetMapping("/expenses/{id}")
    @Operation(summary = "Get an expense", description = "Returns one expense by UUID.")
    @ApiResponse(responseCode = "200", description = "Expense returned")
    @ApiResponse(responseCode = "404", description = "Expense not found")
    public ResponseEntity<Expense> getExpenseById(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        Expense expense = expenseService.getExpenseById(id, authenticatedUserId(jwt))
                .orElseThrow(() -> new ExpenseNotFoundException(id));
        return ResponseEntity.ok(expense);
    }

    @PostMapping("/groups/{groupId}/expenses")
    @Operation(summary = "Add an expense", description = "Adds a validated expense to a group for an authenticated group member.")
    @ApiResponse(responseCode = "201", description = "Expense created")
    @ApiResponse(responseCode = "400", description = "Request validation failed")
    @ApiResponse(responseCode = "404", description = "Group not found")
    public ResponseEntity<Expense> addExpense(
            @PathVariable UUID groupId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody Expense expense) {
        UUID madeByUserId = authenticatedUserId(jwt);
        Expense createdExpense = expenseService.addExpense(groupId, expense, madeByUserId);
        return ResponseEntity.created(URI.create("/api/expenses/" + createdExpense.getId()))
                .body(createdExpense);
    }

    @PutMapping("/expenses/{id}")
    @Operation(summary = "Update an expense", description = "Updates an expense for an authenticated member of its group.")
    @ApiResponse(responseCode = "200", description = "Expense updated")
    @ApiResponse(responseCode = "400", description = "Request validation failed")
    @ApiResponse(responseCode = "404", description = "Expense not found")
    public ResponseEntity<Expense> updateExpense(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody Expense expense) {
        Expense updatedExpense = expenseService.updateExpense(id, expense, authenticatedUserId(jwt))
                .orElseThrow(() -> new ExpenseNotFoundException(id));
        return ResponseEntity.ok(updatedExpense);
    }

    @DeleteMapping("/expenses/{id}")
    @Operation(summary = "Delete an expense", description = "Deletes an expense for an authenticated member of its group.")
    @ApiResponse(responseCode = "204", description = "Expense deleted")
    @ApiResponse(responseCode = "404", description = "Expense not found")
    public ResponseEntity<Void> deleteExpense(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        if (!expenseService.deleteExpense(id, authenticatedUserId(jwt))) {
            throw new ExpenseNotFoundException(id);
        }
        return ResponseEntity.noContent().build();
    }

    private UUID authenticatedUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
