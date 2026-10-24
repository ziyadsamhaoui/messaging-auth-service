package com.ziyadsamhaoui.messagingauthservice.outbox;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayTest {

    private static final String TOPIC = "badrlink.auth.credential.v1";

    private OutboxEventRepository repository;
    private KafkaTemplate<String, String> kafkaTemplate;
    private OutboxRelay relay;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(OutboxEventRepository.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        relay = new OutboxRelay(repository, kafkaTemplate, new ObjectMapper(),
                new TransactionTemplate(transactionManager), 50, 5, TOPIC);
    }

    private OutboxEvent event(String aggregateId, int attempts) {
        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("Credential")
                .aggregateId(aggregateId)
                .eventType("CREDENTIAL_REGISTERED")
                .eventVersion(1)
                .payload("{\"credentialId\":\"x\"}")
                .createdAt(Instant.now())
                .attempts(attempts)
                .nextRetryAt(Instant.now())
                .build();
    }

    @Test
    void poisonRowDoesNotBlockTheRestOfTheBatch() {
        OutboxEvent bad = event("bad", 0);
        OutboxEvent good = event("good", 0);
        when(repository.lockPendingBatch(50)).thenReturn(List.of(bad, good));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenAnswer(invocation -> {
            ProducerRecord<String, String> record = invocation.getArgument(0);
            return "bad".equals(record.key())
                    ? CompletableFuture.failedFuture(new RuntimeException("kafka down"))
                    : CompletableFuture.completedFuture(null);
        });

        relay.publishPending();

        verify(repository).recordFailure(eq(bad.getId()), eq(1), anyString(), eq("PENDING"), any(Instant.class));
        verify(repository).markProcessed(good.getId());
        verify(repository, never()).markProcessed(bad.getId());
    }

    @Test
    void fifthConsecutiveFailureMovesToDeadLetter() {
        OutboxEvent poison = event("poison", 4);
        when(repository.lockPendingBatch(50)).thenReturn(List.of(poison));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka down")));

        relay.publishPending();

        verify(repository).recordFailure(eq(poison.getId()), eq(5), anyString(),
                eq("FAILED_DEAD_LETTER"), any(Instant.class));
        verify(repository, never()).markProcessed(any());
    }

    @Test
    void failureBelowTheThresholdStaysPending() {
        OutboxEvent poison = event("poison", 3);
        when(repository.lockPendingBatch(50)).thenReturn(List.of(poison));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka down")));

        relay.publishPending();

        verify(repository).recordFailure(eq(poison.getId()), eq(4), anyString(),
                eq("PENDING"), any(Instant.class));
    }

    @Test
    void failureErrorIsTruncatedTo1000Characters() {
        OutboxEvent poison = event("poison", 0);
        when(repository.lockPendingBatch(50)).thenReturn(List.of(poison));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("x".repeat(5000))));

        relay.publishPending();

        ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
        verify(repository).recordFailure(eq(poison.getId()), eq(1), error.capture(),
                eq("PENDING"), any(Instant.class));
        assertThat(error.getValue()).hasSize(OutboxRelay.MAX_ERROR_LENGTH);
    }

    @Test
    void backoffGrowsWithTheAttemptCount() {
        OutboxEvent poison = event("poison", 2);
        when(repository.lockPendingBatch(50)).thenReturn(List.of(poison));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka down")));

        Instant lowerBound = Instant.now().plusSeconds(7);
        relay.publishPending();
        Instant upperBound = Instant.now().plusSeconds(9);

        ArgumentCaptor<Instant> nextRetryAt = ArgumentCaptor.forClass(Instant.class);
        verify(repository).recordFailure(eq(poison.getId()), eq(3), anyString(),
                eq("PENDING"), nextRetryAt.capture());
        assertThat(nextRetryAt.getValue()).isBetween(lowerBound, upperBound);
    }

    @Test
    void emptyBatchPublishesNothing() {
        when(repository.lockPendingBatch(50)).thenReturn(List.of());

        relay.publishPending();

        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
        verify(repository, never()).markProcessed(any());
        verify(repository, never()).recordFailure(any(), anyInt(), any(), any(), any());
    }

    @Test
    void storedTraceParentIsInjectedIntoTheKafkaRecord() {
        String traceParent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        OutboxEvent event = event("credential-1", 0);
        event.setTraceParent(traceParent);
        when(repository.lockPendingBatch(50)).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        relay.publishPending();

        ArgumentCaptor<ProducerRecord<String, String>> record = recordCaptor();
        verify(kafkaTemplate).send(record.capture());
        assertThat(record.getValue().topic()).isEqualTo(TOPIC);
        assertThat(record.getValue().headers().lastHeader("traceparent")).isNotNull();
        assertThat(new String(record.getValue().headers().lastHeader("traceparent").value()))
                .isEqualTo(traceParent);
        verify(repository).markProcessed(event.getId());
    }

    @Test
    void rowsWithoutTraceContextPublishNoTraceparentHeader() {
        OutboxEvent event = event("credential-2", 0);
        when(repository.lockPendingBatch(50)).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        relay.publishPending();

        ArgumentCaptor<ProducerRecord<String, String>> record = recordCaptor();
        verify(kafkaTemplate).send(record.capture());
        assertThat(record.getValue().headers().lastHeader("traceparent")).isNull();
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<ProducerRecord<String, String>> recordCaptor() {
        return (ArgumentCaptor<ProducerRecord<String, String>>) (ArgumentCaptor<?>) ArgumentCaptor
                .forClass(ProducerRecord.class);
    }
}
