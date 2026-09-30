package com.ziyadsamhaoui.messagingauthservice.web;

/**
 * Sprint 6 §3.1 — per-request holder for the Gateway's X-Correlation-Id so the
 * outbox publisher can carry it into the event stream. Absent header → a fresh
 * id, so events are still traceable end-to-end when Auth is called directly.
 */
public final class CorrelationContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private CorrelationContext() {
    }

    public static String current() {
        return CURRENT.get();
    }

    public static void set(String correlationId) {
        CURRENT.set(correlationId);
    }

    public static void clear() {
        CURRENT.remove();
    }
}
