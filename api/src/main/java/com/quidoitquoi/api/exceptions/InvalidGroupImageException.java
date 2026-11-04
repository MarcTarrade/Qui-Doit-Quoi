package com.quidoitquoi.api.exceptions;

public class InvalidGroupImageException extends RuntimeException {
    private final String messageKey;

    public InvalidGroupImageException(String messageKey) {
        super(messageKey);
        this.messageKey = messageKey;
    }

    public InvalidGroupImageException(String messageKey, Throwable cause) {
        super(messageKey, cause);
        this.messageKey = messageKey;
    }

    public String getMessageKey() {
        return messageKey;
    }
}
