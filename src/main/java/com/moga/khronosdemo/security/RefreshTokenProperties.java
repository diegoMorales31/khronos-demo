package com.moga.khronosdemo.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Sessions behind the refresh token (app.session.*).
 *
 * @param idleTtl        a session not used for this long ends; each use starts the count again
 * @param maxPerUser     signed-in devices a user can have; signing in on one more ends the one used least recently
 * @param reuseGrace     a replaced token sent again within this time is taken as a retry or a second tab, not a theft
 */
@ConfigurationProperties(prefix = "app.session")
public record RefreshTokenProperties(Duration idleTtl, int maxPerUser, Duration reuseGrace) {
}
