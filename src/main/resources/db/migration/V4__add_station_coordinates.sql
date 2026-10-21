ALTER TABLE stations
    ADD COLUMN latitude  DOUBLE PRECISION,
    ADD COLUMN longitude DOUBLE PRECISION;

ALTER TABLE stations
    ADD CONSTRAINT chk_stations_lat CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    ADD CONSTRAINT chk_stations_lng CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    ADD CONSTRAINT chk_stations_coords_together CHECK ((latitude IS NULL) = (longitude IS NULL));
