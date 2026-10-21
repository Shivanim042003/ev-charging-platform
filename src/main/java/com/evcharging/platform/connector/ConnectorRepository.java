package com.evcharging.platform.connector;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConnectorRepository extends JpaRepository<Connector, UUID> {

    List<Connector> findByStation_Id(UUID stationId);

    List<Connector> findByStation_IdAndStatus(UUID stationId, ConnectorStatus status);

    Optional<Connector> findByIdAndStation_Id(UUID id, UUID stationId);

    boolean existsByStation_IdAndConnectorNumber(UUID stationId, String connectorNumber);

    // Emits SELECT ... FOR UPDATE: serializes concurrent bookings on one connector.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Connector c where c.id = :id")
    Optional<Connector> findByIdForUpdate(@Param("id") UUID id);
}
