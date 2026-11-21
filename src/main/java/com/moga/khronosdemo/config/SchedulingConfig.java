package com.moga.khronosdemo.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Background jobs (@Scheduled): today only the clean-up of old notifications
@Configuration
@EnableScheduling
@EnableConfigurationProperties(NotificationProperties.class)
public class SchedulingConfig {
}
