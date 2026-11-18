package com.moga.khronosdemo.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

// The clock and the time-tracking settings (app.time-tracking.*)
@Configuration
@EnableConfigurationProperties(TimeTrackingProperties.class)
public class ClockConfig {

    // The server's notion of "now" for clock-ins, rates and reports. Tests replace it to fix the time.
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
