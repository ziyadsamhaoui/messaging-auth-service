package com.ziyadsamhaoui.messagingauthservice.exception;

public class DuplicateEmailException extends AuthException {
    public DuplicateEmailException(String email) {
        super("email already registered: " + email);
    }
}
