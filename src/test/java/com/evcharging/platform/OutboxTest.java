package com.evcharging.platform;

import com.evcharging.platform.connector.Connector;
import com.evcharging.platform.connector.ConnectorRepository;
import com.evcharging.platform.reservation.ReservationRepository;
import com.evcharging.platform.reservation.ReservationService;
import com.evcharging.platform.reservation.dto.CreateReservationRequest;
import com.evcharging.platform.reservation.dto.ReservationResponse;
import com.evcharging.platform.station.Station;
import com.evcharging.platform.station.StationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class OutboxTest {

    @Autowired StationRepository stationRepository;
    @Autowired ConnectorRepository connectorRepository;
    @Autowired ReservationRepository reservationRepository;
    @Autowired ReservationService reservationService;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager txManager;

    private Connector newConnector(String name) {
        Station s = stationRepository.save(new Station(name, "1 Outbox St"));
        return connectorRepository.save(new Connector(s, "1", new BigDecimal("50.00")));
    }

    private int outboxCount(UUID aggregateId, String type) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE aggregate_id = ? AND event_type = ?",
                Integer.class, aggregateId, type);
        return n == null ? 0 : n;
    }

    private boolean isPublished(UUID aggregateId, String type) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE aggregate_id = ? AND event_type = ? AND published_at IS NOT NULL",
                Integer.class, aggregateId, type);
        return n != null && n > 0;
    }

    @Test
    void bookingWritesOutboxRowAndRelayPublishesIt() throws Exception {
        Connector c = newConnector("Outbox Station A");
        OffsetDateTime start = OffsetDateTime.now().plusDays(9).truncatedTo(ChronoUnit.HOURS);
        ReservationResponse r = reservationService.create(
                new CreateReservationRequest(c.getId(), "outbox", start, start.plusHours(1)));

        assertEquals(1, outboxCount(r.id(), "BOOKING_CONFIRMED"));

        long deadline = System.currentTimeMillis() + 15_000;
        while (!isPublished(r.id(), "BOOKING_CONFIRMED") && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        assertTrue(isPublished(r.id(), "BOOKING_CONFIRMED"), "relay should mark the row published");
    }

    @Test
    void rolledBackBookingLeavesNoOutboxRow() {
        Connector c = newConnector("Outbox Station B");
        OffsetDateTime start = OffsetDateTime.now().plusDays(9).truncatedTo(ChronoUnit.HOURS);
        AtomicReference<UUID> id = new AtomicReference<>();

        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            ReservationResponse r = reservationService.create(
                    new CreateReservationRequest(c.getId(), "rollback", start, start.plusHours(1)));
            id.set(r.id());
            status.setRollbackOnly();
        });

        assertEquals(0, outboxCount(id.get(), "BOOKING_CONFIRMED"));
        assertFalse(reservationRepository.existsById(id.get()));
    }
}
