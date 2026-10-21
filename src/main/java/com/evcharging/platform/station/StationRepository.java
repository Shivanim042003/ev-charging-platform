package com.evcharging.platform.station;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface StationRepository extends JpaRepository<Station, UUID> {

    List<Station> findByStatus(StationStatus status);

    // Haversine great-circle distance in km (earth radius 6371 km), nearest first.
    @Query(value = """
            SELECT s.* FROM stations s
            WHERE s.status = 'ACTIVE'
              AND s.latitude IS NOT NULL
              AND 6371 * 2 * ASIN(SQRT(
                    POWER(SIN(RADIANS(s.latitude - :lat) / 2), 2) +
                    COS(RADIANS(:lat)) * COS(RADIANS(s.latitude)) *
                    POWER(SIN(RADIANS(s.longitude - :lng) / 2), 2))) <= :radiusKm
            ORDER BY
              6371 * 2 * ASIN(SQRT(
                    POWER(SIN(RADIANS(s.latitude - :lat) / 2), 2) +
                    COS(RADIANS(:lat)) * COS(RADIANS(s.latitude)) *
                    POWER(SIN(RADIANS(s.longitude - :lng) / 2), 2)))
            """, nativeQuery = true)
    List<Station> findNearby(@Param("lat") double lat,
                             @Param("lng") double lng,
                             @Param("radiusKm") double radiusKm);
}
