package com.ziyadsamhaoui.messagingauthservice.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query(value = """
            select * from outbox_events
            where status = 'PENDING'
              and next_retry_at <= CURRENT_TIMESTAMP
            order by created_at asc
            limit :batch
            for update skip locked
            """, nativeQuery = true)
    List<OutboxEvent> lockPendingBatch(@Param("batch") int batch);

    @Modifying(clearAutomatically = true)
    @Query(value = """
            update outbox_events
            set status = 'PROCESSED',
                published_at = CURRENT_TIMESTAMP
            where id = :id
            """, nativeQuery = true)
    int markProcessed(@Param("id") UUID id);

    @Modifying(clearAutomatically = true)
    @Query(value = """
            update outbox_events
            set attempts = :attempts,
                last_error = :error,
                status = :status,
                next_retry_at = :nextRetryAt
            where id = :id
            """, nativeQuery = true)
    int recordFailure(@Param("id") UUID id,
                      @Param("attempts") int attempts,
                      @Param("error") String error,
                      @Param("status") String status,
                      @Param("nextRetryAt") Instant nextRetryAt);
}
