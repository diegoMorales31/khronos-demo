package com.moga.khronosdemo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Web Push settings (app.push.*). Without both VAPID keys push is off: the app runs as before and
 * GET /api/push-subscriptions/public-key answers 204.
 *
 * @param vapidPublicKey  the uncompressed P-256 public key, base64url (65 bytes), as web-push generate-vapid-keys prints it
 * @param vapidPrivateKey the matching private key, base64url (32 bytes)
 * @param subject         a mailto: or https: contact the push services can reach the sender at
 * @param ttl             how long a push service keeps a message for a device that is offline
 * @param allowedHosts    push service hosts a subscription may point at; "*.example.com" also takes its subdomains
 */
@ConfigurationProperties(prefix = "app.push")
public record PushProperties(
        String vapidPublicKey,
        String vapidPrivateKey,
        String subject,
        Duration ttl,
        Duration connectTimeout,
        Duration readTimeout,
        List<String> allowedHosts
) {

    public boolean enabled() {
        return hasText(vapidPublicKey) && hasText(vapidPrivateKey);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
