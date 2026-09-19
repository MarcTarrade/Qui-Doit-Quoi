package com.quidoitquoi.api.exceptions;

import java.util.UUID;

public class MemberNotFoundException extends RuntimeException {
    public MemberNotFoundException(UUID id) {
        super("Member with id " + id + " was not found");
    }
}