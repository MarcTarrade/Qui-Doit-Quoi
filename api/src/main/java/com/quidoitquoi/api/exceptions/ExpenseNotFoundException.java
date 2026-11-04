package com.quidoitquoi.api.exceptions;

import java.util.UUID;

public class ExpenseNotFoundException extends RuntimeException {
    private final UUID id;

    public ExpenseNotFoundException(UUID id) {
        super("Expense with id " + id + " was not found");
        this.id = id;
    }

    public UUID getId() {
        return id;
    }
}
