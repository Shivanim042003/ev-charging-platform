package com.evcharging.platform.station;

import com.evcharging.platform.station.dto.StationResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class StationQueryService {

    private final StationRepository stationRepository;

    public StationQueryService(StationRepository stationRepository) {
        this.stationRepository = stationRepository;
    }

    @Cacheable(cacheNames = "station", key = "#id")
    @Transactional(readOnly = true)
    public StationResponse getCached(UUID id) {
        return load(id);
    }

    @Transactional(readOnly = true)
    public StationResponse getUncached(UUID id) {
        return load(id);
    }

    private StationResponse load(UUID id) {
        return stationRepository.findById(id)
                .map(StationResponse::from)
                .orElseThrow(() -> new StationNotFoundException(id));
    }
}
