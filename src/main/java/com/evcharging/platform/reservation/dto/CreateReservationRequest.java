package com.evcharging.platform.reservation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

/** userRef is filled in from the authenticated user by the controller; a client-sent value is ignored. */
public record CreateReservationRequest(
        @NotNull
        UUID connectorId,

        @Size(max = 100)
        String userRef,

        @NotNull
        OffsetDateTime startTime,

        @NotNull
        OffsetDateTime endTime
) {
}