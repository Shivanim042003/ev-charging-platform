# EV Charging Slot Reservation Platform

A Spring Boot service for browsing EV charging stations and reserving connector time slots.
Java 17, Spring Boot 4.1, PostgreSQL 16, Redis, Kafka, Flyway, Docker.

The design goal is correctness under concurrency: two drivers can never hold the same connector
at the same time, and booking events are never lost between the database and Kafka.

## What it does

| Area | Behaviour |
|------|-----------|
| Stations & connectors | Create, list, deactivate/reactivate. Nothing is hard-deleted; rows are marked `INACTIVE`. A station or connector with upcoming confirmed reservations cannot be deactivated. |
| Reservations | Book a connector for a future window of up to 8 hours; cancel (idempotent); list by user. |
| Demand pricing | `base x min(1 + bookedHours/24, 1.5)` where `bookedHours` is how much of that UTC day is already booked on the connector. The quote is stored on the reservation. |
| Station discovery | `GET /api/v1/stations/nearby` - Haversine distance in SQL, nearest first, radius up to 500 km. |
| Station cache | `GET /api/v1/stations/{id}` is served from Redis (60 s TTL) and evicted after the commit of any status change. |
| Notifications | Booking events flow through a transactional outbox to Kafka (`booking-events`); a consumer logs `NOTIFY ...`. |

## How double-booking is prevented

Two independent layers:

1. **Row lock.** `ReservationService.create` runs `SELECT ... FOR UPDATE` on the connector row
   (`PESSIMISTIC_WRITE`), so concurrent bookings for one connector queue up, then check for overlap.
2. **Database constraint.** Migration V2 adds a GiST exclusion constraint,
   `EXCLUDE USING gist (connector_id WITH =, tstzrange(start_time, end_time, '[)') WITH &&) WHERE (status = 'CONFIRMED')`.
   Even if application code were wrong, PostgreSQL rejects an overlapping confirmed booking.
   Half-open ranges mean 10:00-11:00 and 11:00-12:00 do not conflict; cancelled bookings free the slot.

`ConcurrentBookingTest` fires 50 concurrent booking attempts for the same slot and asserts exactly
one succeeds, 49 are rejected as conflicts, none fail unexpectedly, and one row exists.

## Reliable events (transactional outbox)

Booking events are not sent to Kafka inside the request. `BookingEventPublisher` inserts a row into
`outbox_events` in the **same transaction** as the reservation (`Propagation.MANDATORY`), so the booking
and its event commit or roll back together. `OutboxRelay` (every 500 ms) claims unpublished rows, sends
them to Kafka, and marks them published.

- Delivery is **at-least-once**; consumers should dedupe on reservation id + event type.
- Several relay instances are safe: rows are claimed with `FOR UPDATE SKIP LOCKED` plus a 60 s lease
  (`claimed_at`, migration V6). A crashed relay's rows are retried after the lease expires.
- Events are sent in creation order. If one event fails, later events for the same booking are held back.
- With several relays running, ordering across relays is not guaranteed.

## Run it

Requires Docker.

```bash
docker compose up -d --build
curl http://localhost:8081/actuator/health
```

Ports: app `8081`, Postgres `5433`, Redis `6379`, Kafka `9092`.

Create data and book a slot:

```bash
STATION=$(curl -s -X POST localhost:8081/api/v1/stations -H 'Content-Type: application/json' \
  -d '{"name":"Downtown","address":"1 Main St","latitude":44.9778,"longitude":-93.2650}' | jq -r .id)

CONNECTOR=$(curl -s -X POST localhost:8081/api/v1/stations/$STATION/connectors \
  -H 'Content-Type: application/json' -d '{"connectorNumber":"A1","powerKw":150}' | jq -r .id)

curl -s -X POST localhost:8081/api/v1/reservations -H 'Content-Type: application/json' \
  -d "{\"connectorId\":\"$CONNECTOR\",\"userRef\":\"driver-1\",\"startTime\":\"2030-01-15T15:00:00Z\",\"endTime\":\"2030-01-15T16:00:00Z\"}"
```

A second booking for an overlapping time returns `409 Conflict`.
Watch the notification consumer with `docker compose logs -f app | grep NOTIFY`.

## API

| Method | Path | Notes |
|--------|------|-------|
| POST | `/api/v1/stations` | name, address, optional latitude + longitude |
| GET | `/api/v1/stations` | active stations |
| GET | `/api/v1/stations/nearby?lat=&lng=&radiusKm=` | radius 0.01-500, default 10 |
| GET | `/api/v1/stations/{id}` | cached |
| PATCH | `/api/v1/stations/{id}/deactivate` or `/activate` | deactivate blocked by upcoming reservations |
| POST | `/api/v1/stations/{id}/connectors` | connectorNumber, powerKw |
| GET | `/api/v1/stations/{id}/connectors?activeOnly=true` | |
| PATCH | `/api/v1/stations/{id}/connectors/{cid}/deactivate` or `/activate` | |
| POST | `/api/v1/reservations` | connectorId, userRef, startTime, endTime (ISO-8601 with offset) |
| PATCH | `/api/v1/reservations/{id}/cancel` | idempotent |
| GET | `/api/v1/reservations?userRef=` | newest first |

Errors use RFC 7807 problem details: `400` validation, `404` not found, `409` conflicts.

## Tests

```bash
mvn test        # or ./mvnw test if you add the Maven wrapper
```

Integration tests start throwaway PostgreSQL 16, Redis and Kafka containers with Testcontainers, so
Docker must be running. They cover: concurrent booking, the cache and its eviction, Kafka event delivery,
outbox atomicity and rollback, multi-relay claiming, pricing, UTC-day maths, deactivation rules,
idempotent cancel and nearby search.

`CacheBenchmarkTest` prints cached vs. database read latency. On a laptop with local containers,
cached reads were roughly 20-50% lower in mean latency across repeated runs; treat this as a local
benchmark, not a production guarantee.

## Project layout

```
src/main/java/com/evcharging/platform
  station/       stations, Haversine search, Redis-cached reads
  connector/     connectors and the row-lock query
  reservation/   booking, cancellation, pricing inputs
  pricing/       demand pricing rule
  notification/  outbox, relay, Kafka consumer
  common/        error handling
  config/        cache and scheduling
src/main/resources/db/migration   Flyway V1-V6
```

## Known limitations

- No authentication or authorization; `userRef` is a plain string.
- Outbox delivery is at-least-once, not exactly-once.
- Prices are quotes stored on the reservation; there is no payment or metering.
- The Maven wrapper is not included; use an installed Maven or run `mvn wrapper:wrapper`.
