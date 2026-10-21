package com.evcharging.platform.reservation;

import com.evcharging.platform.connector.Connector;
import com.evcharging.platform.connector.ConnectorRepository;
import com.evcharging.platform.reservation.dto.CreateReservationRequest;
import com.evcharging.platform.station.Station;
import com.evcharging.platform.station.StationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Import(com.evcharging.platform.TestcontainersConfiguration.class)
@SpringBootTest
class ConcurrentBookingTest {

    private static final int REQUESTS = 50;

    @Autowired StationRepository stationRepository;
    @Autowired ConnectorRepository connectorRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReservationService reservationService;

    @Test
    void fiftyConcurrentRequestsForSameSlotProduceExactlyOneReservation() throws Exception {
        Station station = stationRepository.save(new Station("Test Station", "1 Test St"));
        Connector connector = connectorRepository.save(
                new Connector(station, "1", new BigDecimal("150.00")));

        OffsetDateTime start = OffsetDateTime.now().plusDays(30).truncatedTo(ChronoUnit.HOURS);
        OffsetDateTime end = start.plusHours(1);

        ExecutorService pool = Executors.newFixedThreadPool(REQUESTS);
        CountDownLatch ready = new CountDownLatch(REQUESTS);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < REQUESTS; i++) {
            final String user = "user-" + i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    reservationService.create(
                            new CreateReservationRequest(connector.getId(), user, start, end));
                    succeeded.incrementAndGet();
                } catch (ReservationConflictException e) {
                    conflicts.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                    unexpected.incrementAndGet();
                }
            }));
        }

        ready.await(30, TimeUnit.SECONDS);
        go.countDown();
        for (Future<?> f : futures) {
            f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertEquals(1, succeeded.get(), "exactly one booking must succeed");
        assertEquals(REQUESTS - 1, conflicts.get(), "all others must be rejected as conflicts");
        assertEquals(0, unexpected.get(), "no unexpected errors");
        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM reservations WHERE connector_id = ?", Integer.class, connector.getId());
        assertEquals(1, rows, "exactly one row in the database");
    }
}
