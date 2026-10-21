package com.evcharging.platform.reservation;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UtcDayHoursTest {

    private static final OffsetDateTime DAY = OffsetDateTime.of(2030, 1, 15, 0, 0, 0, 0, ZoneOffset.UTC);

    private static OffsetDateTime utc(int day, int hour) {
        return OffsetDateTime.of(2030, 1, day, hour, 0, 0, 0, ZoneOffset.UTC);
    }

    @Test
    void intervalInsideTheDayCountsFully() {
        assertEquals(3.0, ReservationService.hoursWithinUtcDay(utc(15, 10), utc(15, 13), DAY));
    }

    @Test
    void intervalCrossingMidnightIsClippedToTheDay() {
        // 22:00 on the 15th to 02:00 on the 16th: only 2 hours fall on the 15th.
        assertEquals(2.0, ReservationService.hoursWithinUtcDay(utc(15, 22), utc(16, 2), DAY));
        // ...and 2 hours fall on the 16th.
        assertEquals(2.0, ReservationService.hoursWithinUtcDay(utc(15, 22), utc(16, 2), utc(16, 0)));
    }

    @Test
    void intervalOutsideTheDayCountsNothingAndOffsetsAreNormalised() {
        assertEquals(0.0, ReservationService.hoursWithinUtcDay(utc(16, 1), utc(16, 4), DAY));

        // 20:00-23:00 at UTC-05:00 is 01:00-04:00 UTC on the 16th, so nothing on the 15th.
        OffsetDateTime s = OffsetDateTime.of(2030, 1, 15, 20, 0, 0, 0, ZoneOffset.ofHours(-5));
        OffsetDateTime e = OffsetDateTime.of(2030, 1, 15, 23, 0, 0, 0, ZoneOffset.ofHours(-5));
        assertEquals(0.0, ReservationService.hoursWithinUtcDay(s, e, DAY));
        assertEquals(3.0, ReservationService.hoursWithinUtcDay(s, e, utc(16, 0)));
    }
}
