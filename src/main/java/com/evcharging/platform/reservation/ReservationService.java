package com.evcharging.platform.reservation;

import com.evcharging.platform.connector.Connector;
import com.evcharging.platform.connector.ConnectorNotFoundException;
import com.evcharging.platform.connector.ConnectorRepository;
import com.evcharging.platform.notification.BookingEventPublisher;
import com.evcharging.platform.pricing.PricingService;
import com.evcharging.platform.reservation.dto.CreateReservationRequest;
import com.evcharging.platform.reservation.dto.ReservationResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class ReservationService {

    private static final Duration MAX_DURATION = Duration.ofHours(8);

    private final ReservationRepository reservationRepository;
    private final ConnectorRepository connectorRepository;
    private final BookingEventPublisher eventPublisher;
    private final PricingService pricingService;

    public ReservationService(ReservationRepository reservationRepository,
                              ConnectorRepository connectorRepository,
                              BookingEventPublisher eventPublisher,
                              PricingService pricingService) {
        this.reservationRepository = reservationRepository;
        this.connectorRepository = connectorRepository;
        this.eventPublisher = eventPublisher;
        this.pricingService = pricingService;
    }

    @Transactional
    public ReservationResponse create(CreateReservationRequest request) {
        UUID connectorId = request.connectorId();
        OffsetDateTime start = request.startTime();
        OffsetDateTime end = request.endTime();

        if (!end.isAfter(start)) {
            throw new InvalidReservationException("End time must be after start time");
        }
        if (start.isBefore(OffsetDateTime.now())) {
            throw new InvalidReservationException("Start time must be in the future");
        }
        if (Duration.between(start, end).compareTo(MAX_DURATION) > 0) {
            throw new InvalidReservationException("A reservation cannot exceed 8 hours");
        }

        // SELECT ... FOR UPDATE: concurrent bookings for this connector queue up here.
        Connector connector = connectorRepository.findByIdForUpdate(connectorId)
                .orElseThrow(() -> new ConnectorNotFoundException(connectorId));

        if (!connector.isActive() || !connector.getStation().isActive()) {
            throw new ReservationConflictException("Connector or station is not active");
        }

        boolean overlaps = reservationRepository
                .existsByConnector_IdAndStatusAndStartTimeLessThanAndEndTimeGreaterThan(
                        connectorId, ReservationStatus.CONFIRMED, end, start);
        if (overlaps) {
            throw new ReservationConflictException("The requested time slot is already booked");
        }

        // Demand pricing: how much of the booking's UTC day is already taken on this connector.
        OffsetDateTime dayStart = start.withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);
        OffsetDateTime dayEnd = dayStart.plusDays(1);
        double bookedHours = reservationRepository
                .findOverlappingWindow(connectorId, ReservationStatus.CONFIRMED, dayStart, dayEnd)
                .stream()
                .mapToDouble(r -> hoursWithinUtcDay(r.getStartTime(), r.getEndTime(), dayStart))
                .sum();

        Reservation toSave = new Reservation(connector, request.userRef().trim(), start, end);
        toSave.setQuotedPricePerKwh(pricingService.quote(connector.getBasePricePerKwh(), bookedHours));

        // The database exclusion constraint is the final safety net behind the lock above.
        Reservation saved = reservationRepository.saveAndFlush(toSave);
        eventPublisher.publish("BOOKING_CONFIRMED", saved);
        return ReservationResponse.from(saved);
    }

    /** Hours of [intervalStart, intervalEnd) that fall inside the UTC day containing {@code dayStart}. */
    static double hoursWithinUtcDay(OffsetDateTime intervalStart,
                                    OffsetDateTime intervalEnd,
                                    OffsetDateTime dayStart) {
        OffsetDateTime start = intervalStart.withOffsetSameInstant(ZoneOffset.UTC);
        OffsetDateTime end = intervalEnd.withOffsetSameInstant(ZoneOffset.UTC);
        OffsetDateTime utcDayStart = dayStart.withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);
        OffsetDateTime utcDayEnd = utcDayStart.plusDays(1);

        OffsetDateTime clippedStart = start.isBefore(utcDayStart) ? utcDayStart : start;
        OffsetDateTime clippedEnd = end.isAfter(utcDayEnd) ? utcDayEnd : end;
        if (!clippedEnd.isAfter(clippedStart)) {
            return 0.0;
        }
        return Duration.between(clippedStart, clippedEnd).toMinutes() / 60.0;
    }

    /**
     * Cancels a reservation only if it belongs to ownerRef. Someone else's reservation is reported
     * as not found, so its existence is not revealed. Runs in one transaction.
     */
    @Transactional
    public ReservationResponse cancel(UUID reservationId, String ownerRef) {
        Reservation reservation = reservationRepository.findByIdAndUserRef(reservationId, ownerRef)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        return cancelEntity(reservation);
    }

    /** Idempotent: cancelling an already cancelled reservation changes nothing and emits no event. */
    @Transactional
    public ReservationResponse cancel(UUID reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        return cancelEntity(reservation);
    }

    private ReservationResponse cancelEntity(Reservation reservation) {
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            return ReservationResponse.from(reservation);
        }
        reservation.cancel();
        Reservation saved = reservationRepository.saveAndFlush(reservation);
        eventPublisher.publish("BOOKING_CANCELLED", saved);
        return ReservationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> listForUser(String userRef) {
        return reservationRepository.findByUserRefOrderByStartTimeDesc(userRef).stream()
                .map(ReservationResponse::from)
                .toList();
    }
}