package com.ziyadsamhaoui.messagingauthservice.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.ziyadsamhaoui.messagingauthservice.web.CorrelationContext;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;


@Component
@RequiredArgsConstructor
public class TransactionalOutboxPublisher implements OutboxPublisher {

    public static final String TOPIC = "badrlink.auth.credential.v1";
    public static final String AGGREGATE_TYPE = "Credential";

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String aggregateType, String aggregateId, String eventType, Object payload) {
        outboxEventRepository.save(OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .eventVersion(EventEnvelope.VERSION_1)
                .payload(objectMapper.writeValueAsString(payload))
                .correlationId(CorrelationContext.current())
                .build());
    }

    @Override
    public String topicFor(String eventType) {
        return TOPIC;
    }

    @Override
    public ObjectMapper objectMapper() {
        return objectMapper;
    }
}
