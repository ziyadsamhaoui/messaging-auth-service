package com.ziyadsamhaoui.messagingauthservice.exception;

public class InvalidCredentialsException extends AuthException {
    public InvalidCredentialsException() {
        super("invalid email or password");
    }
}
