package com.ziyadsamhaoui.messagingauthservice.outbox;

import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;


public record EventEnvelope(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String producer,
        String correlationId,
        String aggregateId,
        Object payload) {

    public static final String PRODUCER = "messaging-auth-service";
    public static final int VERSION_1 = 1;

    public static EventEnvelope of(String eventType, String aggregateId, Object payload) {
        return new EventEnvelope(UUID.randomUUID(), eventType, VERSION_1, Instant.now(),
                PRODUCER, null, aggregateId, payload);
    }

    public String toJson(ObjectMapper objectMapper) {
        return objectMapper.writeValueAsString(this);
    }
}
