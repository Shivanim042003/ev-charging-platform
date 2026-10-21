package com.evcharging.platform.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 50;

    private final OutboxEventRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate transactionTemplate;

    public OutboxRelay(
            OutboxEventRepository outbox,
            KafkaTemplate<String, String> kafka,
            PlatformTransactionManager txManager) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.transactionTemplate = new TransactionTemplate(txManager);
    }

    @Scheduled(fixedDelayString = "${outbox.relay.interval-ms:500}")
    public void relay() {
        List<UUID> ids = transactionTemplate.execute(
                status -> outbox.claimPendingIds(BATCH_SIZE));

        if (ids == null || ids.isEmpty()) {
            return;
        }

        List<OutboxEvent> events = outbox.findAllByIdIn(ids).stream()
                .sorted(Comparator.comparing(OutboxEvent::getCreatedAt))
                .toList();

        // A booking whose earlier event failed must not have later events sent ahead of it.
        // Skipped rows stay claimed until the lease expires, then are retried in order.
        Set<UUID> failedAggregates = new HashSet<>();

        for (OutboxEvent event : events) {
            if (failedAggregates.contains(event.getAggregateId())) {
                continue;
            }
            try {
                kafka.send(
                        BookingEventPublisher.TOPIC,
                        event.getAggregateId().toString(),
                        event.getPayload()
                ).get(5, TimeUnit.SECONDS);

                outbox.markPublishedById(event.getId());

            } catch (Exception ex) {
                outbox.recordFailureById(event.getId());
                failedAggregates.add(event.getAggregateId());

                log.warn("Outbox event {} failed: {}", event.getId(), ex.getMessage());

                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
}
