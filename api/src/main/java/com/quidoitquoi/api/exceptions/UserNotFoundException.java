package com.quidoitquoi.api.exceptions;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {
    private final UUID id;

    public UserNotFoundException(UUID id) {
        super("User with id " + id + " was not found");
        this.id = id;
    }

    public UUID getId() {
        return id;
    }
}
