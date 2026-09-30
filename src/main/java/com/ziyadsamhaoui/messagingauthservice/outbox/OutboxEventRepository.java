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
            where published_at is null
            order by created_at
            limit :batch
            for update skip locked
            """, nativeQuery = true)
    List<OutboxEvent> lockUnpublishedBatch(@Param("batch") int batch);

    // Separate from the publish call, the gap between them is what makes redelivery (and consumer idempotency) mandatory.
    @Modifying
    @Query("update OutboxEvent o set o.publishedAt = :publishedAt where o.id in :ids")
    int markPublished(@Param("ids") List<UUID> ids, @Param("publishedAt") Instant publishedAt);
}
