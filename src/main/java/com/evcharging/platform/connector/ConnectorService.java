package com.evcharging.platform.connector;

import com.evcharging.platform.connector.dto.ConnectorResponse;
import com.evcharging.platform.connector.dto.CreateConnectorRequest;
import com.evcharging.platform.reservation.ReservationConflictException;
import com.evcharging.platform.reservation.ReservationRepository;
import com.evcharging.platform.reservation.ReservationStatus;
import com.evcharging.platform.station.Station;
import com.evcharging.platform.station.StationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ConnectorService {

    private final ConnectorRepository connectorRepository;
    private final StationService stationService;
    private final ReservationRepository reservationRepository;

    public ConnectorService(ConnectorRepository connectorRepository, StationService stationService,
                            ReservationRepository reservationRepository) {
        this.connectorRepository = connectorRepository;
        this.stationService = stationService;
        this.reservationRepository = reservationRepository;
    }

    @Transactional
    public ConnectorResponse create(UUID stationId, CreateConnectorRequest request) {
        Station station = stationService.getById(stationId);
        String number = request.connectorNumber().trim();
        if (connectorRepository.existsByStation_IdAndConnectorNumber(stationId, number)) {
            throw new DuplicateConnectorException(number);
        }
        Connector saved = connectorRepository.saveAndFlush(
                new Connector(station, number, request.powerKw()));
        return ConnectorResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ConnectorResponse> list(UUID stationId, boolean activeOnly) {
        stationService.getById(stationId);
        List<Connector> connectors = activeOnly
                ? connectorRepository.findByStation_IdAndStatus(stationId, ConnectorStatus.ACTIVE)
                : connectorRepository.findByStation_Id(stationId);
        return connectors.stream().map(ConnectorResponse::from).toList();
    }

    @Transactional
    public ConnectorResponse deactivate(UUID stationId, UUID connectorId) {
        Connector connector = find(stationId, connectorId);
        if (reservationRepository.existsByConnector_IdAndStatusAndEndTimeAfter(
                connectorId, ReservationStatus.CONFIRMED, OffsetDateTime.now())) {
            throw new ReservationConflictException(
                    "Connector has upcoming confirmed reservations; cancel them first");
        }
        connector.deactivate();
        return ConnectorResponse.from(connectorRepository.saveAndFlush(connector));
    }

    @Transactional
    public ConnectorResponse activate(UUID stationId, UUID connectorId) {
        Connector connector = find(stationId, connectorId);
        connector.activate();
        return ConnectorResponse.from(connectorRepository.saveAndFlush(connector));
    }

    private Connector find(UUID stationId, UUID connectorId) {
        return connectorRepository.findByIdAndStation_Id(connectorId, stationId)
                .orElseThrow(() -> new ConnectorNotFoundException(connectorId));
    }
}
