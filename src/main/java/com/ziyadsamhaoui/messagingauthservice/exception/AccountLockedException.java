package com.ziyadsamhaoui.messagingauthservice.exception;

import java.time.Instant;

public class AccountLockedException extends AuthException {
    private final Instant lockoutEnd;

    public AccountLockedException(Instant lockoutEnd) {
        super("account is locked until " + lockoutEnd);
        this.lockoutEnd = lockoutEnd;
    }

    public Instant getLockoutEnd() {
        return lockoutEnd;
    }
}
