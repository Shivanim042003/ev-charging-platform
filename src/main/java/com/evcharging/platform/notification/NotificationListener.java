package com.evcharging.platform.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    @KafkaListener(topics = BookingEventPublisher.TOPIC, groupId = "notifications")
    public void onBookingEvent(String payload) {
        log.info("NOTIFY {}", payload);
    }
}
