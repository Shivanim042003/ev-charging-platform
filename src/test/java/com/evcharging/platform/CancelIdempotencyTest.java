package com.evcharging.platform;

import com.evcharging.platform.connector.Connector;
import com.evcharging.platform.connector.ConnectorRepository;
import com.evcharging.platform.reservation.ReservationRepository;
import com.evcharging.platform.reservation.ReservationService;
import com.evcharging.platform.reservation.ReservationStatus;
import com.evcharging.platform.reservation.dto.CreateReservationRequest;
import com.evcharging.platform.reservation.dto.ReservationResponse;
import com.evcharging.platform.station.Station;
import com.evcharging.platform.station.StationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CancelIdempotencyTest {

    @Autowired StationRepository stationRepository;
    @Autowired ConnectorRepository connectorRepository;
    @Autowired ReservationRepository reservationRepository;
    @Autowired ReservationService reservationService;

    @Test
    void secondCancelDoesNotTouchTheStoredRow() {
        Station s = stationRepository.save(new Station("Idem Station", "1 Idem St"));
        Connector c = connectorRepository.save(new Connector(s, "1", new BigDecimal("50.00")));
        OffsetDateTime start = OffsetDateTime.now().plusDays(7).truncatedTo(ChronoUnit.HOURS);
        ReservationResponse r = reservationService.create(
                new CreateReservationRequest(c.getId(), "idem", start, start.plusHours(1)));

        reservationService.cancel(r.id());
        OffsetDateTime storedAfterFirst = reservationRepository.findById(r.id()).orElseThrow().getUpdatedAt();

        ReservationResponse second = reservationService.cancel(r.id());
        OffsetDateTime storedAfterSecond = reservationRepository.findById(r.id()).orElseThrow().getUpdatedAt();

        assertEquals(ReservationStatus.CANCELLED, second.status());
        assertEquals(storedAfterFirst.toInstant(), storedAfterSecond.toInstant(),
                "second cancel must not write to the row");
    }
}
