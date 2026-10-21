package com.evcharging.platform.connector;

import com.evcharging.platform.connector.dto.ConnectorResponse;
import com.evcharging.platform.connector.dto.CreateConnectorRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/v1/stations/{stationId}/connectors")
public class ConnectorController {

    private final ConnectorService connectorService;

    public ConnectorController(ConnectorService connectorService) {
        this.connectorService = connectorService;
    }

    @PostMapping
    public ResponseEntity<ConnectorResponse> create(
            @PathVariable UUID stationId,
            @Valid @RequestBody CreateConnectorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(connectorService.create(stationId, request));
    }

    @GetMapping
    public List<ConnectorResponse> list(
            @PathVariable UUID stationId,
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        return connectorService.list(stationId, activeOnly);
    }

    @PatchMapping("/{connectorId}/deactivate")
    public ConnectorResponse deactivate(@PathVariable UUID stationId, @PathVariable UUID connectorId) {
        return connectorService.deactivate(stationId, connectorId);
    }

    @PatchMapping("/{connectorId}/activate")
    public ConnectorResponse activate(@PathVariable UUID stationId, @PathVariable UUID connectorId) {
        return connectorService.activate(stationId, connectorId);
    }
}
