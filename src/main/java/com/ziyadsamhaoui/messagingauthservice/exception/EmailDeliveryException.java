package com.ziyadsamhaoui.messagingauthservice.exception;

public class EmailDeliveryException extends AuthException {
    public EmailDeliveryException(String message) {
        super(message);
    }

    public EmailDeliveryException(String message, Throwable cause) {
        super(message);
        initCause(cause);
    }
}
