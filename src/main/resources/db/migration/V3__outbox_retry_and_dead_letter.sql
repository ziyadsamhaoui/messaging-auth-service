ALTER TABLE outbox_events
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN last_error TEXT,
    ADD COLUMN next_retry_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

DROP INDEX idx_outbox_unpublished;

CREATE INDEX idx_outbox_pending ON outbox_events (status, next_retry_at, created_at)
    WHERE status = 'PENDING';
