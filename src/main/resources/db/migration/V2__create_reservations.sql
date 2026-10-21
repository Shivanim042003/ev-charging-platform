CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE reservations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    connector_id UUID NOT NULL REFERENCES connectors(id) ON DELETE RESTRICT,
    user_ref VARCHAR(100) NOT NULL,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    status VARCHAR(15) NOT NULL DEFAULT 'CONFIRMED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_reservation_time CHECK (end_time > start_time),
    CONSTRAINT chk_reservation_status
        CHECK (status IN ('CONFIRMED', 'CANCELLED')),

    -- No two CONFIRMED bookings for the same connector may overlap.
    -- Half-open [) ranges: a booking ending at 11:00 does not clash with one starting at 11:00.
    CONSTRAINT ex_reservation_no_overlap
        EXCLUDE USING gist (
            connector_id WITH =,
            tstzrange(start_time, end_time, '[)') WITH &&
        )
        WHERE (status = 'CONFIRMED')
);

CREATE INDEX idx_reservations_status
    ON reservations (status);

CREATE INDEX idx_reservations_user_ref
    ON reservations (user_ref);
