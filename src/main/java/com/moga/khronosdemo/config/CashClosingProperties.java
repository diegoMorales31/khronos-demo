package com.moga.khronosdemo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * @param differenceThreshold the largest difference between counted and expected cash, in euros either
 *                            way, that a day can be closed with without giving a reason
 */
@ConfigurationProperties(prefix = "app.cash-closing")
public record CashClosingProperties(
        BigDecimal differenceThreshold
) {

    public CashClosingProperties {
        if (differenceThreshold == null || differenceThreshold.signum() < 0
                || differenceThreshold.stripTrailingZeros().scale() > 2) {
            throw new IllegalStateException("app.cash-closing.difference-threshold must be set, must not be negative"
                    + " and can have at most two decimals");
        }
    }
}
