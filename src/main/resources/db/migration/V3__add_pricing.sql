ALTER TABLE connectors
    ADD COLUMN base_price_per_kwh NUMERIC(8, 4) NOT NULL DEFAULT 0.3000;

ALTER TABLE connectors
    ADD CONSTRAINT chk_connectors_price_positive CHECK (base_price_per_kwh > 0);

ALTER TABLE reservations
    ADD COLUMN quoted_price_per_kwh NUMERIC(8, 4);
