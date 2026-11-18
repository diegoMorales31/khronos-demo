package com.moga.khronosdemo.entities;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Euro amounts as they are stored and returned: two decimals. Amounts entered are validated to two
 * decimals, and sums and differences of them keep two, so setting the scale never rounds them; it only
 * makes 12.5 come back as 12.50. HALF_UP covers anything that might still need rounding.
 */
public final class Money {

    private Money() {
    }

    public static BigDecimal euros(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
