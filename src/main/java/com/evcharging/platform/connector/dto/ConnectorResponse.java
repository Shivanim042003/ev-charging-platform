package com.evcharging.platform.connector.dto;

import com.evcharging.platform.connector.Connector;
import com.evcharging.platform.connector.ConnectorStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ConnectorResponse(
        UUID id,
        UUID stationId,
        String connectorNumber,
        BigDecimal powerKw,
        ConnectorStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ConnectorResponse from(Connector c) {
        return new ConnectorResponse(
                c.getId(),
                c.getStation().getId(),
                c.getConnectorNumber(),
                c.getPowerKw(),
                c.getStatus(),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }
}
