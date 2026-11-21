package com.moga.khronosdemo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** The in-app notifications (app.notifications.*): notices older than retention are deleted every night. */
@ConfigurationProperties(prefix = "app.notifications")
public record NotificationProperties(Duration retention) {
}
