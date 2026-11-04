package com.quidoitquoi.api.exceptions;

import java.util.UUID;

public class GroupNotFoundException extends RuntimeException {
    private final UUID id;

    public GroupNotFoundException(UUID id) {
        super("Group with id " + id + " was not found");
        this.id = id;
    }

    public UUID getId() {
        return id;
    }
}