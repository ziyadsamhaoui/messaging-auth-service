package com.ziyadsamhaoui.messagingauthservice.outbox;

import com.ziyadsamhaoui.messagingauthservice.observability.TraceContextCarrier;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;


@Slf4j
@RequiredArgsConstructor
public class OutboxRelay {

    static final int MAX_ERROR_LENGTH = 1000;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final int batchSize;
    private final int maxAttempts;
    private final String topic;

    @Scheduled(fixedDelayString = "${badrlink.kafka.outbox.relay-interval:500ms}")
    public void publishPending() {
        List<OutboxEvent> batch = fetchPendingBatch();
        for (OutboxEvent event : batch) {
            if (!publishOne(event)) {
                return;
            }
        }
    }

    private List<OutboxEvent> fetchPendingBatch() {
        List<OutboxEvent> batch = transactionTemplate.execute(status ->
                outboxEventRepository.lockPendingBatch(batchSize));
        return batch == null ? List.of() : batch;
    }

    boolean publishOne(OutboxEvent event) {
        try {
            send(event);
            transactionTemplate.executeWithoutResult(status ->
                    outboxEventRepository.markProcessed(event.getId()));
            log.debug("outbox event {} published to {}", event.getId(), topic);
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            recordFailure(event, ex);
            return false;
        } catch (Exception ex) {
            recordFailure(event, ex);
            return true;
        }
    }

    private void send(OutboxEvent event) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, event.getAggregateId(),
                objectMapper.writeValueAsString(toEnvelope(event)));

        // The stored traceparent is written explicitly so the header is present even when the
        // agent is not attached (tests, or a build without instrumentation).
        if (event.getTraceParent() != null) {
            record.headers().add(TraceContextCarrier.TRACEPARENT,
                    event.getTraceParent().getBytes(StandardCharsets.UTF_8));
        }

        Context originating = TraceContextCarrier.extract(event.getTraceParent());
        if (originating == null) {
            // No context to restore (scheduled origin, or a row written before this feature):
            // publish as-is rather than inventing a header that would not match any trace.
            kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
            return;
        }

        // Re-activating the originating context makes the agent's producer span a child of the
        // span that wrote the outbox row, so its own traceparent injection keeps the trace.
        try (Scope scope = originating.makeCurrent()) {
            kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
        }
    }

    private void recordFailure(OutboxEvent event, Exception ex) {
        int attempts = event.getAttempts() + 1;
        OutboxStatus status = attempts >= maxAttempts ? OutboxStatus.FAILED_DEAD_LETTER : OutboxStatus.PENDING;
        Instant nextRetryAt = Instant.now().plusSeconds(1L << attempts);
        String error = truncate(ex.getMessage());
        transactionTemplate.executeWithoutResult(ignored ->
                outboxEventRepository.recordFailure(event.getId(), attempts, error, status.name(), nextRetryAt));
        log.warn("outbox event {} failed on attempt {} with status {}: {}",
                event.getId(), attempts, status, error);
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }

    private EventEnvelope toEnvelope(OutboxEvent event) {
        return new EventEnvelope(
                event.getId(),
                event.getEventType(),
                event.getEventVersion(),
                event.getCreatedAt(),
                EventEnvelope.PRODUCER,
                event.getCorrelationId(),
                event.getAggregateId(),
                objectMapper.readTree(event.getPayload()));
    }
}
