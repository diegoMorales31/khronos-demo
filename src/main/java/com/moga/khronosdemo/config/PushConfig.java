package com.moga.khronosdemo.config;

import com.moga.khronosdemo.notifications.WebPushCrypto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

// Web Push settings (app.push.*). Keys that are set but wrong stop the start, instead of every push failing later
@Configuration
@EnableConfigurationProperties(PushProperties.class)
public class PushConfig {

    private static final Logger log = LoggerFactory.getLogger(PushConfig.class);

    public PushConfig(PushProperties properties) {
        if (properties.enabled()) {
            WebPushCrypto.checkKeyPair(properties.vapidPublicKey(), properties.vapidPrivateKey());
            if (properties.subject() == null || !properties.subject().matches("^(mailto:|https://).+")) {
                throw new IllegalStateException("PUSH_VAPID_SUBJECT must be a mailto: or https: contact");
            }
        } else {
            log.info("Push notifications are off: PUSH_VAPID_PUBLIC_KEY and PUSH_VAPID_PRIVATE_KEY are not set");
        }
    }
}
