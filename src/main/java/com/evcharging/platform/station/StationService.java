package com.evcharging.platform.station;

import com.evcharging.platform.reservation.ReservationConflictException;
import com.evcharging.platform.reservation.ReservationRepository;
import com.evcharging.platform.reservation.ReservationStatus;
import com.evcharging.platform.station.dto.CreateStationRequest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class StationService {

    static final double MAX_RADIUS_KM = 500;

    private final StationRepository stationRepository;
    private final ReservationRepository reservationRepository;
    private final CacheManager cacheManager;

    public StationService(StationRepository stationRepository,
                          ReservationRepository reservationRepository,
                          CacheManager cacheManager) {
        this.stationRepository = stationRepository;
        this.reservationRepository = reservationRepository;
        this.cacheManager = cacheManager;
    }

    @Transactional
    public Station create(CreateStationRequest request) {
        if ((request.latitude() == null) != (request.longitude() == null)) {
            throw new IllegalArgumentException("Latitude and longitude must be provided together");
        }
        return stationRepository.save(new Station(
                request.name().trim(),
                request.address().trim(),
                request.latitude(),
                request.longitude()));
    }

    @Transactional(readOnly = true)
    public Station getById(UUID id) {
        return stationRepository.findById(id)
                .orElseThrow(() -> new StationNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Station> listActive() {
        return stationRepository.findByStatus(StationStatus.ACTIVE);
    }

    /** Active stations within {@code radiusKm} of the point, nearest first. */
    @Transactional(readOnly = true)
    public List<Station> findNearby(double lat, double lng, double radiusKm) {
        if (Double.isNaN(lat) || lat < -90 || lat > 90) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90");
        }
        if (Double.isNaN(lng) || lng < -180 || lng > 180) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180");
        }
        if (Double.isNaN(radiusKm) || radiusKm <= 0 || radiusKm > MAX_RADIUS_KM) {
            throw new IllegalArgumentException(
                    "Radius must be greater than 0 and at most " + (int) MAX_RADIUS_KM + " km");
        }
        return stationRepository.findNearby(lat, lng, radiusKm);
    }

    @Transactional
    public Station deactivate(UUID id) {
        Station station = getById(id);
        if (reservationRepository.existsByConnector_Station_IdAndStatusAndEndTimeAfter(
                id, ReservationStatus.CONFIRMED, OffsetDateTime.now())) {
            throw new ReservationConflictException(
                    "Station has upcoming confirmed reservations; cancel them first");
        }
        station.deactivate();
        Station saved = stationRepository.saveAndFlush(station);
        evictAfterCommit(id);
        return saved;
    }

    @Transactional
    public Station activate(UUID id) {
        Station station = getById(id);
        station.activate();
        Station saved = stationRepository.saveAndFlush(station);
        evictAfterCommit(id);
        return saved;
    }

    // Evict only after the database commit, so a concurrent read cannot re-cache stale data.
    private void evictAfterCommit(UUID id) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                Cache cache = cacheManager.getCache("station");
                if (cache != null) {
                    cache.evict(id);
                }
            }
        });
    }
}
