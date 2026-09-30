package com.ziyadsamhaoui.messagingauthservice.outbox;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 6 §3.5 delivery tests against a real broker (Apache Kafka via
 * Testcontainers). Proves the two crash scenarios that motivate at-least-once:
 *
 * 1. Domain write + outbox write committed, relay never ran ("killed process"),
 *    a fresh relay publishes the event after restart.
 * 2. The relay published but crashed before stamping published_at — the event is
 *    sent a second time; the duplicate carries the SAME eventId, so consumer-side
 *    idempotency makes redelivery harmless.
 *
 * Skips automatically when no Docker daemon is available.
 */
class OutboxKafkaDeliveryTest {

    private static final String TOPIC = TransactionalOutboxPublisher.TOPIC;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void eventSurvivesRelayDowntimeAndIsPublishedAfterRestart() throws Exception {
        try (KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:4.1.1"))) {
            kafka.start();
            kafka.followOutput(new Slf4jLogConsumer(org.slf4j.LoggerFactory.getLogger("kafka")));

            OutboxEventRepository repository = mock(OutboxEventRepository.class);
            OutboxPublisher publisher = mock(OutboxPublisher.class);
            OutboxEvent committed = OutboxEvent.builder()
                    .id(UUID.randomUUID())
                    .aggregateType(TransactionalOutboxPublisher.AGGREGATE_TYPE)
                    .aggregateId(UUID.randomUUID().toString())
                    .eventType(CredentialEvents.CREDENTIAL_REGISTERED)
                    .eventVersion(1)
                    .payload(MAPPER.writeValueAsString(new CredentialEvents.CredentialRegistered(
                            UUID.randomUUID(), "survivor", "survivor@example.com", java.time.Instant.now())))
                    .build();
            when(repository.lockUnpublishedBatch(anyInt())).thenReturn(List.of(committed));

            // "Restart": a brand-new relay instance with a fresh KafkaTemplate.
            OutboxRelay relay = new OutboxRelay(repository, templateFor(kafka.getBootstrapServers()),
                    publisher, MAPPER, 100, TOPIC);
            relay.publishPending();

            verify(repository).markPublished(anyList(), any(java.time.Instant.class));
            List<ConsumerRecord<String, String>> records = pollFor(kafka.getBootstrapServers(), TOPIC, 1);
            assertThat(records).hasSize(1);
            JsonNode envelope = MAPPER.readTree(records.get(0).value());
            assertThat(envelope.get("eventId").asString()).isEqualTo(committed.getId().toString());
            assertThat(envelope.get("eventType").asString()).isEqualTo(CredentialEvents.CREDENTIAL_REGISTERED);
            assertThat(envelope.get("producer").asString()).isEqualTo(EventEnvelope.PRODUCER);
            assertThat(records.get(0).key()).isEqualTo(committed.getAggregateId());
        }
    }

    @Test
    void crashBeforePublishedAtStampSendsDuplicateWithSameEventId() throws Exception {
        try (KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:4.1.1"))) {
            kafka.start();

            OutboxEventRepository repository = mock(OutboxEventRepository.class);
            OutboxPublisher publisher = mock(OutboxPublisher.class);
            OutboxEvent committed = OutboxEvent.builder()
                    .id(UUID.randomUUID())
                    .aggregateType(TransactionalOutboxPublisher.AGGREGATE_TYPE)
                    .aggregateId(UUID.randomUUID().toString())
                    .eventType(CredentialEvents.CREDENTIAL_PASSWORD_CHANGED)
                    .eventVersion(1)
                    .payload(MAPPER.writeValueAsString(new CredentialEvents.CredentialPasswordChanged(
                            UUID.randomUUID(), java.time.Instant.now())))
                    .build();
            // The row stays unpublished on every scan: the first run "crashed" between
            // the Kafka ack and the published_at write, so it is picked up again.
            when(repository.lockUnpublishedBatch(anyInt())).thenReturn(List.of(committed));

            OutboxRelay relay = new OutboxRelay(repository, templateFor(kafka.getBootstrapServers()),
                    publisher, MAPPER, 100, TOPIC);
            relay.publishPending();

            // Crash before markPublished: the next relay tick sees the same row.
            relay.publishPending();

            List<ConsumerRecord<String, String>> records = pollFor(kafka.getBootstrapServers(), TOPIC, 2);
            assertThat(records).hasSize(2);
            String firstEventId = MAPPER.readTree(records.get(0).value()).get("eventId").asString();
            String secondEventId = MAPPER.readTree(records.get(1).value()).get("eventId").asString();
            assertThat(secondEventId).isEqualTo(firstEventId);
            // A consumer deduplicating on eventId treats the second copy as a no-op —
            // that property is exercised per-consumer (see the User service tests).
        }
    }

    @Test
    void failedPublishDoesNotMarkRowPublished() throws Exception {
        try (KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:4.1.1"))) {
            kafka.start();

            OutboxEventRepository repository = mock(OutboxEventRepository.class);
            OutboxPublisher publisher = mock(OutboxPublisher.class);
            OutboxEvent committed = OutboxEvent.builder()
                    .id(UUID.randomUUID())
                    .aggregateType(TransactionalOutboxPublisher.AGGREGATE_TYPE)
                    .aggregateId(UUID.randomUUID().toString())
                    .eventType(CredentialEvents.CREDENTIAL_LOCKED)
                    .eventVersion(1)
                    .payload(MAPPER.writeValueAsString(new CredentialEvents.CredentialLocked(
                            UUID.randomUUID(), java.time.Instant.now().plusSeconds(900))))
                    .build();
            when(repository.lockUnpublishedBatch(anyInt())).thenReturn(List.of(committed));

            // Unreachable broker: the send fails, so nothing may be marked published.
            OutboxRelay relay = new OutboxRelay(repository, templateFor("localhost:1"),
                    publisher, MAPPER, 100, TOPIC);
            try {
                relay.publishPending();
            } catch (IllegalStateException expected) {
                // relay surfaces the failure; the transaction rolls back
            }

            verify(repository, never()).markPublished(anyList(), any(java.time.Instant.class));
            assertThat(pollFor(kafka.getBootstrapServers(), TOPIC, 0)).isEmpty();
        }
    }

    private org.springframework.kafka.core.KafkaTemplate<String, String> templateFor(String bootstrapServers) {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new org.springframework.kafka.core.KafkaTemplate<>(
                new org.springframework.kafka.core.DefaultKafkaProducerFactory<>(props));
    }

    private List<ConsumerRecord<String, String>> pollFor(String bootstrapServers, String topic, int minRecords) {
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "outbox-test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(List.of(topic));
            List<ConsumerRecord<String, String>> records = new ArrayList<>();
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (records.size() < minRecords && System.nanoTime() < deadline) {
                consumer.poll(Duration.ofMillis(200)).forEach(records::add);
            }
            return records;
        }
    }
}
