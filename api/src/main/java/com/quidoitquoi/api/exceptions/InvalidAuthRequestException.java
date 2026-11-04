package com.quidoitquoi.api.exceptions;

public class InvalidAuthRequestException extends RuntimeException {
    private final String messageKey;

    public InvalidAuthRequestException(String messageKey) {
        super(messageKey);
        this.messageKey = messageKey;
    }

    public String getMessageKey() {
        return messageKey;
    }
}
