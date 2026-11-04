package com.quidoitquoi.api.exceptions;

public class EmailAlreadyUsedException extends RuntimeException {
    private final String email;

    public EmailAlreadyUsedException(String email) {
        super("An account already exists for email: " + email);
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
