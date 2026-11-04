package com.quidoitquoi.api.exceptions;

import java.util.UUID;

public class MemberNotFoundException extends RuntimeException {
    private final UUID id;

    public MemberNotFoundException(UUID id) {
        super("Member with id " + id + " was not found");
        this.id = id;
    }

    public UUID getId() {
        return id;
    }
}