package com.evcharging.platform.reservation.dto;

import com.evcharging.platform.reservation.Reservation;
import com.evcharging.platform.reservation.ReservationStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        UUID connectorId,
        String userRef,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        ReservationStatus status,
        BigDecimal quotedPricePerKwh,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ReservationResponse from(Reservation r) {
        return new ReservationResponse(
                r.getId(),
                r.getConnector().getId(),
                r.getUserRef(),
                r.getStartTime(),
                r.getEndTime(),
                r.getStatus(),
                r.getQuotedPricePerKwh(),
                r.getCreatedAt(),
                r.getUpdatedAt());
    }
}
