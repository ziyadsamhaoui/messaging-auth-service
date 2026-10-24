package com.ziyadsamhaoui.messagingauthservice.observability;

import com.ziyadsamhaoui.messagingauthservice.outbox.OutboxEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutboxBacklogMetricsTest {

    private OutboxEventRepository repository;
    private OutboxBacklogMetrics metrics;
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        repository = mock(OutboxEventRepository.class);
        registry = new SimpleMeterRegistry();
        metrics = new OutboxBacklogMetrics(repository, registry);
    }

    @Test
    void gaugesAreRegisteredUpFrontAndStartAtZero() {
        assertThat(registry.get(OutboxBacklogMetrics.UNPUBLISHED_METRIC).gauge().value()).isZero();
        assertThat(registry.get(OutboxBacklogMetrics.OLDEST_AGE_METRIC).gauge().value()).isZero();
    }

    @Test
    void refreshPublishesTheRealOutboxState() {
        when(repository.countUnpublished()).thenReturn(7L);
        when(repository.oldestUnpublishedAgeSeconds()).thenReturn(42.6);

        metrics.refresh();

        assertThat(registry.get(OutboxBacklogMetrics.UNPUBLISHED_METRIC).gauge().value()).isEqualTo(7.0);
        assertThat(registry.get(OutboxBacklogMetrics.OLDEST_AGE_METRIC).gauge().value()).isEqualTo(43.0);
    }

    @Test
    void backlogDrainsBackToZero() {
        when(repository.countUnpublished()).thenReturn(3L, 0L);
        when(repository.oldestUnpublishedAgeSeconds()).thenReturn(12.0, 0.0);

        metrics.refresh();
        assertThat(metrics.unpublishedCount()).isEqualTo(3);

        metrics.refresh();
        assertThat(metrics.unpublishedCount()).isZero();
        assertThat(metrics.oldestAgeSeconds()).isZero();
    }

    @Test
    void databaseFailureKeepsTheLastKnownValue() {
        when(repository.countUnpublished()).thenReturn(5L);
        when(repository.oldestUnpublishedAgeSeconds()).thenReturn(30.0);
        metrics.refresh();

        when(repository.countUnpublished()).thenThrow(new IllegalStateException("database down"));
        metrics.refresh();

        assertThat(metrics.unpublishedCount()).isEqualTo(5);
        assertThat(metrics.oldestAgeSeconds()).isEqualTo(30);
    }
}
