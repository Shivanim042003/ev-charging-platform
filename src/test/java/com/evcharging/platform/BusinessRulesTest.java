package com.evcharging.platform;

import com.evcharging.platform.connector.Connector;
import com.evcharging.platform.connector.ConnectorRepository;
import com.evcharging.platform.connector.ConnectorService;
import com.evcharging.platform.reservation.ReservationConflictException;
import com.evcharging.platform.reservation.ReservationService;
import com.evcharging.platform.reservation.dto.CreateReservationRequest;
import com.evcharging.platform.reservation.dto.ReservationResponse;
import com.evcharging.platform.station.Station;
import com.evcharging.platform.station.StationRepository;
import com.evcharging.platform.station.StationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BusinessRulesTest {

    @Autowired StationRepository stationRepository;
    @Autowired ConnectorRepository connectorRepository;
    @Autowired ReservationService reservationService;
    @Autowired StationService stationService;
    @Autowired ConnectorService connectorService;

    @Test
    void secondBookingOnSameDayIsPricedByUtilization() {
        Station s = stationRepository.save(new Station("Price Station", "1 Price St"));
        Connector c = connectorRepository.save(new Connector(s, "1", new BigDecimal("50.00")));
        OffsetDateTime day = OffsetDateTime.now(ZoneOffset.UTC).plusDays(20).truncatedTo(ChronoUnit.DAYS);

        ReservationResponse first = reservationService.create(new CreateReservationRequest(
                c.getId(), "p1", day.plusHours(2), day.plusHours(8)));
        ReservationResponse second = reservationService.create(new CreateReservationRequest(
                c.getId(), "p2", day.plusHours(10), day.plusHours(11)));

        assertEquals(new BigDecimal("0.3000"), first.quotedPricePerKwh());
        assertEquals(new BigDecimal("0.3750"), second.quotedPricePerKwh());
    }

    @Test
    void cannotDeactivateWithUpcomingReservationsUntilCancelled() {
        Station s = stationRepository.save(new Station("Rule Station", "1 Rule St"));
        Connector c = connectorRepository.save(new Connector(s, "1", new BigDecimal("50.00")));
        OffsetDateTime start = OffsetDateTime.now().plusDays(5).truncatedTo(ChronoUnit.HOURS);
        ReservationResponse r = reservationService.create(
                new CreateReservationRequest(c.getId(), "r1", start, start.plusHours(1)));

        assertThrows(ReservationConflictException.class,
                () -> connectorService.deactivate(s.getId(), c.getId()));
        assertThrows(ReservationConflictException.class,
                () -> stationService.deactivate(s.getId()));

        reservationService.cancel(r.id());

        connectorService.deactivate(s.getId(), c.getId());
        stationService.deactivate(s.getId());
    }
}
