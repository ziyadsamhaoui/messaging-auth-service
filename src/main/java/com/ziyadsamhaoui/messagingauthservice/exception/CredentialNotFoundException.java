package com.ziyadsamhaoui.messagingauthservice.exception;

import java.util.UUID;

public class CredentialNotFoundException extends AuthException {
    public CredentialNotFoundException(UUID id) {
        super("credential not found: " + id);
    }
}
