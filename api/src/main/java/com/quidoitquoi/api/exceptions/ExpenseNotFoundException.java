package com.quidoitquoi.api.exceptions;

import java.util.UUID;

public class ExpenseNotFoundException extends RuntimeException {
    public ExpenseNotFoundException(UUID id) {
        super("Expense with id " + id + " was not found");
    }
}
