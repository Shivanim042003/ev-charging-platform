-- A relay stamps claimed_at when it takes a row; the claim expires after a lease
-- (60 s in OutboxEventRepository), so a crashed relay's rows are retried.
ALTER TABLE outbox_events
    ADD COLUMN claimed_at TIMESTAMPTZ;
