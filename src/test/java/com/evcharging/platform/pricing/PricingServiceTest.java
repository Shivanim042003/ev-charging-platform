package com.evcharging.platform.pricing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PricingServiceTest {

    private final PricingService pricing = new PricingService();
    private final BigDecimal base = new BigDecimal("0.3000");

    @Test
    void emptyDayCostsBasePrice() {
        assertEquals(new BigDecimal("0.3000"), pricing.quote(base, 0));
    }

    @Test
    void halfBookedDay() {
        assertEquals(new BigDecimal("0.4500"), pricing.quote(base, 12));
    }

    @Test
    void sixHoursBooked() {
        assertEquals(new BigDecimal("0.3750"), pricing.quote(base, 6));
    }

    @Test
    void multiplierIsCapped() {
        assertEquals(new BigDecimal("0.4500"), pricing.quote(base, 24));
        assertEquals(new BigDecimal("0.4500"), pricing.quote(base, 100));
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> pricing.quote(base, -1));
        assertThrows(IllegalArgumentException.class, () -> pricing.quote(base, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> pricing.quote(BigDecimal.ZERO, 1));
        assertThrows(IllegalArgumentException.class, () -> pricing.quote(null, 1));
    }
}
