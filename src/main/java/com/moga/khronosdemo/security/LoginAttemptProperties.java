package com.moga.khronosdemo.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.login")
public record LoginAttemptProperties(
        int maxAttempts,
        Duration window,
        int maxAttemptsPerIp,
        int maxAttemptsPerUser,
        Duration userWindow
) {
}
