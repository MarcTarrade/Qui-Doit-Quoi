package com.quidoitquoi.api.exceptions;

public class InvalidAuthRequestException extends RuntimeException {
    public InvalidAuthRequestException(String message) {
        super(message);
    }
}
