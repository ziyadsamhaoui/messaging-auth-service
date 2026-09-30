-- Sprint 6: transactional outbox for domain events (see docs/EVENTS.md and docs/adr/ADR-006-credential-outbox-events.md).
-- Rows are inserted in the SAME transaction as the domain write; a polling relay
-- publishes them to Kafka and stamps published_at afterwards.
CREATE TABLE outbox_events (
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id   VARCHAR(100) NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    event_version  INT NOT NULL,
    payload        JSONB NOT NULL,
    correlation_id VARCHAR(100),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ NULL
);

-- Relay scan index: only unpublished rows, oldest first.
CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;
