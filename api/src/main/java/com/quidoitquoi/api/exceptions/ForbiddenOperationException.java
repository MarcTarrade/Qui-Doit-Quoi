package com.quidoitquoi.api.exceptions;

public class ForbiddenOperationException extends RuntimeException {
    public ForbiddenOperationException() {
        super("You are not allowed to perform this operation");
    }
}
