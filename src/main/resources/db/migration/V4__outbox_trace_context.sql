-- Trace context captured when the outbox row is written, replayed by the relay so the
-- Kafka record continues the originating trace (INC-11).
--
-- Nullable on purpose: rows written before this migration, and rows written by a
-- non-HTTP entry point with no active span, have no context to propagate. The relay
-- publishes those without a header instead of fabricating one.
ALTER TABLE outbox_events
    ADD COLUMN trace_parent VARCHAR(64);
