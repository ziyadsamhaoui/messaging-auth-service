package com.ziyadsamhaoui.messagingauthservice.web;


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
