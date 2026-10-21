package com.evcharging.platform;

import com.evcharging.platform.connector.Connector;
import com.evcharging.platform.connector.ConnectorRepository;
import com.evcharging.platform.reservation.ReservationService;
import com.evcharging.platform.reservation.dto.CreateReservationRequest;
import com.evcharging.platform.reservation.dto.ReservationResponse;
import com.evcharging.platform.station.Station;
import com.evcharging.platform.station.StationRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.kafka.KafkaContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BookingEventTest {

    @Autowired StationRepository stationRepository;
    @Autowired ConnectorRepository connectorRepository;
    @Autowired ReservationService reservationService;
    @Autowired KafkaContainer kafka;

    @Test
    void confirmedAndCancelledEventsArriveAfterCommit() {
        Station station = stationRepository.save(new Station("Event Station", "1 Event St"));
        Connector connector = connectorRepository.save(new Connector(station, "1", new BigDecimal("50.00")));

        OffsetDateTime start = OffsetDateTime.now().plusDays(10).truncatedTo(ChronoUnit.HOURS);
        ReservationResponse r = reservationService.create(
                new CreateReservationRequest(connector.getId(), "event-user", start, start.plusHours(1)));
        reservationService.cancel(r.id());

        List<String> payloads = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.<String, Object>of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + System.nanoTime(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(List.of("booking-events"));
            long deadline = System.currentTimeMillis() + 20_000;
            while (System.currentTimeMillis() < deadline && payloads.size() < 2) {
                for (ConsumerRecord<String, String> rec : consumer.poll(Duration.ofMillis(500))) {
                    if (rec.key().equals(r.id().toString())) {
                        payloads.add(rec.value());
                    }
                }
            }
        }
        System.out.println("EVENTS " + payloads);
        assertTrue(payloads.stream().anyMatch(p -> p.contains("BOOKING_CONFIRMED")), "confirmed event missing");
        assertTrue(payloads.stream().anyMatch(p -> p.contains("BOOKING_CANCELLED")), "cancelled event missing");
    }
}
