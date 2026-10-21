package com.evcharging.platform;

import com.evcharging.platform.notification.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "outbox.relay.interval-ms=3600000")
class OutboxLeaseTest {

    @Autowired OutboxEventRepository outbox;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager txManager;

    @Test
    void concurrentRelaysClaimDisjointRowsWithoutBlocking() throws Exception {
        for (int i = 0; i < 40; i++) {
            jdbc.update("INSERT INTO outbox_events (id, aggregate_id, event_type, payload) VALUES (?, ?, ?, ?)",
                    UUID.randomUUID(), UUID.randomUUID(), "BOOKING_CONFIRMED", "lease-test");
        }
        try {
            CountDownLatch aClaimed = new CountDownLatch(1);
            CountDownLatch bDone = new CountDownLatch(1);

            CompletableFuture<List<UUID>> a = CompletableFuture.supplyAsync(() ->
                    new TransactionTemplate(txManager).execute(s -> {
                        List<UUID> ids = outbox.claimPendingIds(20);
                        aClaimed.countDown();
                        try {
                            bDone.await(15, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return ids;
                    }));

            assertTrue(aClaimed.await(15, TimeUnit.SECONDS), "relay A should have claimed");

            long t0 = System.nanoTime();
            List<UUID> b = new TransactionTemplate(txManager).execute(s -> outbox.claimPendingIds(20));
            long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
            bDone.countDown();

            List<UUID> aIds = a.get(15, TimeUnit.SECONDS);

            Set<UUID> overlap = new HashSet<>(aIds);
            overlap.retainAll(b);
            assertFalse(aIds.isEmpty(), "A should claim rows");
            assertTrue(overlap.isEmpty(), "relays must never claim the same row");
            assertTrue(elapsedMs < 5_000, "B must skip A's locked rows, not wait (took " + elapsedMs + " ms)");
        } finally {
            jdbc.update("DELETE FROM outbox_events WHERE payload = 'lease-test'");
        }
    }
}
