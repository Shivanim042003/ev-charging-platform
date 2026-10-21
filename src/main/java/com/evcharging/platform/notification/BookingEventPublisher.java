package com.evcharging.platform.notification;

import com.evcharging.platform.reservation.Reservation;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class BookingEventPublisher {

    public static final String TOPIC = "booking-events";

    private final OutboxEventRepository outbox;

    public BookingEventPublisher(OutboxEventRepository outbox) {
        this.outbox = outbox;
    }

    /** Writes the event in the caller's transaction, so it commits or rolls back with the booking. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String type, Reservation r) {
        String key = r.getId().toString();
        String payload = String.format(
                "{\"type\":\"%s\",\"reservationId\":\"%s\",\"connectorId\":\"%s\",\"userRef\":\"%s\",\"startTime\":\"%s\",\"endTime\":\"%s\"}",
                type, key, r.getConnector().getId(), esc(r.getUserRef()),
                r.getStartTime().withOffsetSameInstant(java.time.ZoneOffset.UTC),
                r.getEndTime().withOffsetSameInstant(java.time.ZoneOffset.UTC));
        outbox.save(new OutboxEvent(UUID.randomUUID(), r.getId(), type, payload));
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
