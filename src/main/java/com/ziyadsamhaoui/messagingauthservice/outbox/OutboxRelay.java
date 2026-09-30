package com.ziyadsamhaoui.messagingauthservice.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;


@Slf4j
@RequiredArgsConstructor
public class OutboxRelay {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxPublisher outboxPublisher;
    private final ObjectMapper objectMapper;
    private final int batchSize;
    private final String topic;

    @Scheduled(fixedDelayString = "${badrlink.kafka.outbox.relay-interval:500ms}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = outboxEventRepository.lockUnpublishedBatch(batchSize);
        if (batch.isEmpty()) {
            return;
        }
        for (OutboxEvent event : batch) {
            send(event);
        }
        outboxEventRepository.markPublished(
                batch.stream().map(OutboxEvent::getId).toList(), Instant.now());
        log.debug("outbox relay published {} event(s) to {}", batch.size(), topic);
    }

    private void send(OutboxEvent event) {
        try {
            String envelopeJson = objectMapper.writeValueAsString(toEnvelope(event));
            // Sync send: the ack must happen before published_at is stamped,
            // or the row would be marked published without ever reaching Kafka.
            kafkaTemplate.send(topic, event.getAggregateId(), envelopeJson).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("outbox relay interrupted while publishing " + event.getId(), ex);
        } catch (Exception ex) {
            throw new IllegalStateException("outbox relay failed to publish " + event.getId(), ex);
        }
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
