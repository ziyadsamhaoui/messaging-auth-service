package com.ziyadsamhaoui.messagingauthservice.outbox;

import java.time.Instant;
import java.util.UUID;


public final class CredentialEvents {

    private CredentialEvents() {
    }

    public static final String CREDENTIAL_REGISTERED = "CREDENTIAL_REGISTERED";
    public static final String CREDENTIAL_LOCKED = "CREDENTIAL_LOCKED";
    public static final String CREDENTIAL_UNLOCKED = "CREDENTIAL_UNLOCKED";
    public static final String CREDENTIAL_PASSWORD_CHANGED = "CREDENTIAL_PASSWORD_CHANGED";

    public record CredentialRegistered(UUID credentialId, String username, String email, Instant createdAt) {
    }

    public record CredentialLocked(UUID credentialId, Instant lockoutEnd) {
    }

    public record CredentialUnlocked(UUID credentialId) {
    }

    public record CredentialPasswordChanged(UUID credentialId, Instant changedAt) {
    }
}
