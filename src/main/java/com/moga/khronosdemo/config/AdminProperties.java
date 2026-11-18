package com.moga.khronosdemo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(
        String username,
        String password,
        String email
) {

    boolean isComplete() {
        return StringUtils.hasText(username) && StringUtils.hasText(password) && StringUtils.hasText(email);
    }

    boolean isEmpty() {
        return !StringUtils.hasText(username) && !StringUtils.hasText(password) && !StringUtils.hasText(email);
    }
}
