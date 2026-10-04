package com.ziyadsamhaoui.messagingauthservice.outbox;

public enum OutboxStatus {

    PENDING,

    PROCESSED,

    FAILED_DEAD_LETTER
}
