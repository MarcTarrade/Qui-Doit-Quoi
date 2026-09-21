package com.quidoitquoi.api.exceptions;

public class EmailAlreadyUsedException extends RuntimeException {
    public EmailAlreadyUsedException(String email) {
        super("An account already exists for email: " + email);
    }
}
