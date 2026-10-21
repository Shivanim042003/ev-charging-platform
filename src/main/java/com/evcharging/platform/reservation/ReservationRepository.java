package com.evcharging.platform.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    List<Reservation> findByUserRefOrderByStartTimeDesc(String userRef);

    /** Owner-scoped lookup: empty unless the reservation exists AND belongs to userRef. */
    Optional<Reservation> findByIdAndUserRef(UUID id, String userRef);

    boolean existsByConnector_IdAndStatusAndStartTimeLessThanAndEndTimeGreaterThan(
            UUID connectorId,
            ReservationStatus status,
            OffsetDateTime requestedEnd,
            OffsetDateTime requestedStart);

    boolean existsByConnector_IdAndStatusAndEndTimeAfter(
            UUID connectorId,
            ReservationStatus status,
            OffsetDateTime now);

    boolean existsByConnector_Station_IdAndStatusAndEndTimeAfter(
            UUID stationId,
            ReservationStatus status,
            OffsetDateTime now);

    @Query("""
            select r from Reservation r
            where r.connector.id = :connectorId
              and r.status = :status
              and r.startTime < :dayEnd
              and r.endTime > :dayStart
            """)
    List<Reservation> findOverlappingWindow(@Param("connectorId") UUID connectorId,
                                            @Param("status") ReservationStatus status,
                                            @Param("dayStart") OffsetDateTime dayStart,
                                            @Param("dayEnd") OffsetDateTime dayEnd);
}