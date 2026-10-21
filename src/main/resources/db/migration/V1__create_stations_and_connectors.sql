CREATE TABLE stations (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    address VARCHAR(300) NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_stations_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE connectors (
    id UUID PRIMARY KEY,
    station_id UUID NOT NULL,
    connector_number VARCHAR(50) NOT NULL,
    power_kw NUMERIC(7, 2) NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_connectors_station
        FOREIGN KEY (station_id)
        REFERENCES stations(id)
        ON DELETE RESTRICT,

    CONSTRAINT uq_connector_number_per_station
        UNIQUE (station_id, connector_number),

    CONSTRAINT chk_connectors_status
        CHECK (status IN ('ACTIVE', 'INACTIVE')),

    CONSTRAINT chk_connectors_power_positive
        CHECK (power_kw > 0)
);

CREATE INDEX idx_stations_status
    ON stations(status);

CREATE INDEX idx_connectors_station_status
    ON connectors(station_id, status);
