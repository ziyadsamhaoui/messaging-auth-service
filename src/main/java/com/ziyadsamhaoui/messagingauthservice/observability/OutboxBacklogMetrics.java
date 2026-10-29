package com.ziyadsamhaoui.messagingauthservice.observability;

import com.ziyadsamhaoui.messagingauthservice.outbox.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
public class OutboxBacklogMetrics {

    static final String UNPUBLISHED_METRIC = "badrlink.outbox.unpublished";
    static final String OLDEST_AGE_METRIC = "badrlink.outbox.oldest.age.seconds";

    private final OutboxEventRepository outboxEventRepository;
    private final AtomicLong unpublished = new AtomicLong();
    private final AtomicLong oldestAgeSeconds = new AtomicLong();

    public OutboxBacklogMetrics(OutboxEventRepository outboxEventRepository, MeterRegistry meterRegistry) {
        this.outboxEventRepository = outboxEventRepository;
        Gauge.builder(UNPUBLISHED_METRIC, unpublished, AtomicLong::doubleValue)
                .description("Outbox rows that have not been published to Kafka yet")
                .register(meterRegistry);
        Gauge.builder(OLDEST_AGE_METRIC, oldestAgeSeconds, AtomicLong::doubleValue)
                .description("Age in seconds of the oldest unpublished outbox row")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${badrlink.observability.outbox.refresh-interval:15s}")
    public void refresh() {
        try {
            unpublished.set(outboxEventRepository.countUnpublished());
            oldestAgeSeconds.set(Math.round(outboxEventRepository.oldestUnpublishedAgeSeconds()));
        } catch (RuntimeException ex) {
            log.warn("could not refresh outbox backlog metrics: {}", ex.getMessage());
        }
    }

    long unpublishedCount() {
        return unpublished.get();
    }

    long oldestAgeSeconds() {
        return oldestAgeSeconds.get();
    }
}
