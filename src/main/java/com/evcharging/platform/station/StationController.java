package com.evcharging.platform.station;

import com.evcharging.platform.station.dto.CreateStationRequest;
import com.evcharging.platform.station.dto.StationResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stations")
@Validated
public class StationController {

    private final StationService stationService;
    private final StationQueryService stationQueryService;

    public StationController(StationService stationService, StationQueryService stationQueryService) {
        this.stationService = stationService;
        this.stationQueryService = stationQueryService;
    }

    @PostMapping
    public ResponseEntity<StationResponse> create(@Valid @RequestBody CreateStationRequest request) {
        Station station = stationService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(StationResponse.from(station));
    }

    @GetMapping
    public List<StationResponse> listActive() {
        return stationService.listActive().stream().map(StationResponse::from).toList();
    }

    // Declared before /{id} so "nearby" is never parsed as an id.
    @GetMapping("/nearby")
    public List<StationResponse> nearby(
            @RequestParam @DecimalMin("-90") @DecimalMax("90") double lat,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") double lng,
            @RequestParam(defaultValue = "10") @DecimalMin("0.01") @DecimalMax("500") double radiusKm) {
        return stationService.findNearby(lat, lng, radiusKm).stream().map(StationResponse::from).toList();
    }

    @GetMapping("/{id}")
    public StationResponse get(@PathVariable UUID id) {
        return stationQueryService.getCached(id);
    }

    @PatchMapping("/{id}/deactivate")
    public StationResponse deactivate(@PathVariable UUID id) {
        return StationResponse.from(stationService.deactivate(id));
    }

    @PatchMapping("/{id}/activate")
    public StationResponse activate(@PathVariable UUID id) {
        return StationResponse.from(stationService.activate(id));
    }
}
