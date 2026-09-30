package com.ziyadsamhaoui.messagingauthservice.outbox;

import java.time.Instant;
import java.util.UUID;


public final class CredentialEvents {

    private CredentialEvents() {
    }

    // Event type names — AGGREGATE_PAST_TENSE_VERB per Part 1, Rule 4.
    public static final String CREDENTIAL_REGISTERED = "CREDENTIAL_REGISTERED";
    public static final String CREDENTIAL_LOCKED = "CREDENTIAL_LOCKED";
    public static final String CREDENTIAL_UNLOCKED = "CREDENTIAL_UNLOCKED";
    public static final String CREDENTIAL_PASSWORD_CHANGED = "CREDENTIAL_PASSWORD_CHANGED";

    // Username here is the username-at-registration, Auth originates it once and User owns it afterwards.
    public record CredentialRegistered(UUID credentialId, String username, String email, Instant createdAt) {
    }

    public record CredentialLocked(UUID credentialId, Instant lockoutEnd) {
    }

    public record CredentialUnlocked(UUID credentialId) {
    }

    /** No password data in the payload, deliberately. */
    public record CredentialPasswordChanged(UUID credentialId, Instant changedAt) {
    }
}
