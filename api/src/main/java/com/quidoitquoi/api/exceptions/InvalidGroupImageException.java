package com.quidoitquoi.api.exceptions;

public class InvalidGroupImageException extends RuntimeException {

    public InvalidGroupImageException(String message) {
        super(message);
    }

    public InvalidGroupImageException(String message, Throwable cause) {
        super(message, cause);
    }
}
