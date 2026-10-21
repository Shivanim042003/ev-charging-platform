package com.evcharging.platform.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateReservationRequest(
        @NotNull
        UUID connectorId,

        @NotBlank
        @Size(max = 100)
        String userRef,

        @NotNull
        OffsetDateTime startTime,

        @NotNull
        OffsetDateTime endTime
) {
}
