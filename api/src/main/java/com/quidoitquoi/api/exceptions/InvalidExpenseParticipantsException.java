package com.quidoitquoi.api.exceptions;

public class InvalidExpenseParticipantsException extends RuntimeException {
    public InvalidExpenseParticipantsException() {
        super("api.error.invalid-expense-participants");
    }
}
