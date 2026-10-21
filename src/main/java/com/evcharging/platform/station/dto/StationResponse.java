package com.evcharging.platform.station.dto;

import com.evcharging.platform.station.Station;
import com.evcharging.platform.station.StationStatus;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

// Serializable because it is stored in the Redis cache (JDK serialization).
public record StationResponse(
        UUID id,
        String name,
        String address,
        Double latitude,
        Double longitude,
        StationStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) implements Serializable {

    public static StationResponse from(Station station) {
        return new StationResponse(
                station.getId(),
                station.getName(),
                station.getAddress(),
                station.getLatitude(),
                station.getLongitude(),
                station.getStatus(),
                station.getCreatedAt(),
                station.getUpdatedAt());
    }
}
