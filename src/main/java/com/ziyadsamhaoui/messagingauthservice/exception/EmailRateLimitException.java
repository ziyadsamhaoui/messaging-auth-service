package com.ziyadsamhaoui.messagingauthservice.exception;

public class EmailRateLimitException extends AuthException {
    public EmailRateLimitException(String message) {
        super(message);
    }
}
