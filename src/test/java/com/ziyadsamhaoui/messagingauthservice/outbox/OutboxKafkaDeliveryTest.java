package com.ziyadsamhaoui.messagingauthservice.outbox;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxKafkaDeliveryTest {

    private static final String TOPIC = TransactionalOutboxPublisher.TOPIC;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static TransactionTemplate transactionTemplate() {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        return new TransactionTemplate(transactionManager);
    }

    @Test
    void eventSurvivesRelayDowntimeAndIsPublishedAfterRestart() throws Exception {
        try (KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:4.1.1"))) {
            kafka.start();
            kafka.followOutput(new Slf4jLogConsumer(org.slf4j.LoggerFactory.getLogger("kafka")));

            OutboxEventRepository repository = mock(OutboxEventRepository.class);
            OutboxEvent committed = OutboxEvent.builder()
                    .id(UUID.randomUUID())
                    .aggregateType(TransactionalOutboxPublisher.AGGREGATE_TYPE)
                    .aggregateId(UUID.randomUUID().toString())
                    .eventType(CredentialEvents.CREDENTIAL_REGISTERED)
                    .eventVersion(1)
                    .payload(MAPPER.writeValueAsString(new CredentialEvents.CredentialRegistered(
                            UUID.randomUUID(), "survivor", "survivor@example.com", Instant.now())))
                    .createdAt(Instant.now())
                    .build();
            when(repository.lockPendingBatch(anyInt())).thenReturn(List.of(committed));

            OutboxRelay relay = new OutboxRelay(repository, templateFor(kafka.getBootstrapServers()),
                    MAPPER, transactionTemplate(), 50, 5, TOPIC);
            relay.publishPending();

            verify(repository).markProcessed(committed.getId());
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
    void crashBeforeProcessedStampSendsDuplicateWithSameEventId() throws Exception {
        try (KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:4.1.1"))) {
            kafka.start();

            OutboxEventRepository repository = mock(OutboxEventRepository.class);
            OutboxEvent committed = OutboxEvent.builder()
                    .id(UUID.randomUUID())
                    .aggregateType(TransactionalOutboxPublisher.AGGREGATE_TYPE)
                    .aggregateId(UUID.randomUUID().toString())
                    .eventType(CredentialEvents.CREDENTIAL_PASSWORD_CHANGED)
                    .eventVersion(1)
                    .payload(MAPPER.writeValueAsString(new CredentialEvents.CredentialPasswordChanged(
                            UUID.randomUUID(), Instant.now())))
                    .createdAt(Instant.now())
                    .build();
            when(repository.lockPendingBatch(anyInt())).thenReturn(List.of(committed));

            OutboxRelay relay = new OutboxRelay(repository, templateFor(kafka.getBootstrapServers()),
                    MAPPER, transactionTemplate(), 50, 5, TOPIC);
            relay.publishPending();
            relay.publishPending();

            List<ConsumerRecord<String, String>> records = pollFor(kafka.getBootstrapServers(), TOPIC, 2);
            assertThat(records).hasSize(2);
            String firstEventId = MAPPER.readTree(records.get(0).value()).get("eventId").asString();
            String secondEventId = MAPPER.readTree(records.get(1).value()).get("eventId").asString();
            assertThat(secondEventId).isEqualTo(firstEventId);
        }
    }

    @Test
    void failedPublishIsRecordedAsRetryAndNeverMarkedProcessed() throws Exception {
        try (KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:4.1.1"))) {
            kafka.start();

            OutboxEventRepository repository = mock(OutboxEventRepository.class);
            OutboxEvent committed = OutboxEvent.builder()
                    .id(UUID.randomUUID())
                    .aggregateType(TransactionalOutboxPublisher.AGGREGATE_TYPE)
                    .aggregateId(UUID.randomUUID().toString())
                    .eventType(CredentialEvents.CREDENTIAL_LOCKED)
                    .eventVersion(1)
                    .payload(MAPPER.writeValueAsString(new CredentialEvents.CredentialLocked(
                            UUID.randomUUID(), Instant.now().plusSeconds(900))))
                    .createdAt(Instant.now())
                    .build();
            when(repository.lockPendingBatch(anyInt())).thenReturn(List.of(committed));

            OutboxRelay relay = new OutboxRelay(repository, templateFor("localhost:1"),
                    MAPPER, transactionTemplate(), 50, 5, TOPIC);
            relay.publishPending();

            verify(repository).recordFailure(eq(committed.getId()), eq(1), anyString(),
                    eq(OutboxStatus.PENDING.name()), any(Instant.class));
            verify(repository, never()).markProcessed(any());
            assertThat(pollFor(kafka.getBootstrapServers(), TOPIC, 0)).isEmpty();
        }
    }

    private KafkaTemplate<String, String> templateFor(String bootstrapServers) {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
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
