package com.evcharging.platform.pricing;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class PricingService {

    public static final BigDecimal MAX_MULTIPLIER = new BigDecimal("1.5");
    private static final BigDecimal HOURS_PER_DAY = new BigDecimal("24");

    /**
     * Demand-based price: {@code base * min(1 + bookedHours / 24, 1.5)}, rounded to 4 decimals.
     * {@code bookedHoursThatDay} is how much of the (UTC) day is already booked.
     */
    public BigDecimal quote(BigDecimal basePricePerKwh, double bookedHoursThatDay) {
        if (basePricePerKwh == null || basePricePerKwh.signum() <= 0) {
            throw new IllegalArgumentException("Base price must be positive");
        }
        if (Double.isNaN(bookedHoursThatDay) || bookedHoursThatDay < 0) {
            throw new IllegalArgumentException("Booked hours must be zero or more");
        }
        BigDecimal utilization = BigDecimal.valueOf(bookedHoursThatDay)
                .divide(HOURS_PER_DAY, 6, RoundingMode.HALF_UP);
        BigDecimal multiplier = BigDecimal.ONE.add(utilization).min(MAX_MULTIPLIER);
        return basePricePerKwh.multiply(multiplier).setScale(4, RoundingMode.HALF_UP);
    }
}
