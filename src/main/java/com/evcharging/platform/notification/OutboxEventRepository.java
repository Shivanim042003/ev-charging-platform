package com.evcharging.platform.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Atomically claims up to {@code batchSize} unpublished events that are unclaimed or whose
     * 60 s lease has expired. FOR UPDATE SKIP LOCKED lets concurrent relays take disjoint rows
     * without blocking each other. Must run inside a transaction.
     */
    @Query(value = """
            WITH candidates AS (
                SELECT id
                FROM outbox_events
                WHERE published_at IS NULL
                  AND (claimed_at IS NULL
                       OR claimed_at < CURRENT_TIMESTAMP - INTERVAL '60 seconds')
                ORDER BY created_at
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED
            )
            UPDATE outbox_events e
            SET claimed_at = CURRENT_TIMESTAMP
            FROM candidates c
            WHERE e.id = c.id
            RETURNING e.id
            """, nativeQuery = true)
    List<UUID> claimPendingIds(@Param("batchSize") int batchSize);

    @Transactional(readOnly = true)
    @Query("SELECT e FROM OutboxEvent e WHERE e.id IN :ids")
    List<OutboxEvent> findAllByIdIn(@Param("ids") List<UUID> ids);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE outbox_events
            SET published_at = CURRENT_TIMESTAMP,
                claimed_at = NULL
            WHERE id = :id
              AND published_at IS NULL
            """, nativeQuery = true)
    int markPublishedById(@Param("id") UUID id);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE outbox_events
            SET attempts = attempts + 1,
                claimed_at = NULL
            WHERE id = :id
              AND published_at IS NULL
            """, nativeQuery = true)
    int recordFailureById(@Param("id") UUID id);
}
