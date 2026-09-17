package com.quidoitquoi.api.exceptions;

import java.util.UUID;

public class GroupNotFoundException extends RuntimeException {
    public GroupNotFoundException(UUID id) {
        super("Group with id " + id + " was not found");
    }
}