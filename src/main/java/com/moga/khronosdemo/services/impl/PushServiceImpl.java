package com.moga.khronosdemo.services.impl;

import com.moga.khronosdemo.config.PushProperties;
import com.moga.khronosdemo.dtos.request.PushSubscriptionRequestDTO;
import com.moga.khronosdemo.entities.PushSubscription;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.exceptions.BusinessRuleException;
import com.moga.khronosdemo.exceptions.InvalidRequestException;
import com.moga.khronosdemo.notifications.PushMessage;
import com.moga.khronosdemo.notifications.WebPushClient;
import com.moga.khronosdemo.notifications.WebPushCrypto;
import com.moga.khronosdemo.repositories.PushSubscriptionRepository;
import com.moga.khronosdemo.repositories.UserRepository;
import com.moga.khronosdemo.services.PushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Devices that get push notifications, and the sending. The server posts to whatever endpoint a
 * browser gives, so only https endpoints on the known push services (app.push.allowed-hosts) are
 * accepted: anything else would let a user make the server call an address of their choosing.
 */
@Service
@Transactional
public class PushServiceImpl implements PushService {

    private static final Logger log = LoggerFactory.getLogger(PushServiceImpl.class);
    // Enough for a phone, a tablet and a few browsers; registering one more drops the oldest
    static final int MAX_DEVICES_PER_USER = 10;

    private final PushSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final WebPushClient webPushClient;
    private final PushProperties properties;
    private final Clock clock;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public PushServiceImpl(PushSubscriptionRepository subscriptionRepository, UserRepository userRepository,
                           WebPushClient webPushClient, PushProperties properties, Clock clock) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.webPushClient = webPushClient;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> publicKey() {
        return properties.enabled() ? Optional.of(properties.vapidPublicKey()) : Optional.empty();
    }

    // The same device signing in again, or someone else on it, updates the one row for its endpoint
    @Override
    public void subscribe(Long userId, PushSubscriptionRequestDTO request) {
        if (!properties.enabled()) {
            throw new BusinessRuleException("Push notifications are not set up on this server");
        }
        checkEndpoint(request.endpoint());
        checkKeys(request.keys());

        User user = userRepository.getReferenceById(userId);
        PushSubscription subscription = subscriptionRepository.findByEndpoint(request.endpoint())
                .orElseGet(() -> new PushSubscription(user, request.endpoint(), request.keys().p256dh(), request.keys().auth()));
        subscription.setUser(user);
        subscription.setP256dh(request.keys().p256dh());
        subscription.setAuth(request.keys().auth());
        subscription.setCreatedAt(clock.instant());
        subscriptionRepository.save(subscription);

        List<PushSubscription> devices = subscriptionRepository.findByUser_IdOrderByCreatedAtDesc(userId);
        if (devices.size() > MAX_DEVICES_PER_USER) {
            subscriptionRepository.deleteAll(devices.subList(MAX_DEVICES_PER_USER, devices.size()));
        }
    }

    // Only the caller's own device: someone else's endpoint is left alone, with the same 204
    @Override
    public void unsubscribe(Long userId, String endpoint) {
        subscriptionRepository.deleteByUserIdAndEndpoint(userId, endpoint);
    }

    @Override
    public void unsubscribeAll(Long userId) {
        subscriptionRepository.deleteByUserId(userId);
    }

    // Runs on the notification thread, after the change has committed; each device is tried on its own.
    // No transaction around it, so no database connection is held while the push services answer
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void send(Long userId, PushMessage message) {
        if (!properties.enabled()) {
            return;
        }
        byte[] payload = jsonMapper.writeValueAsBytes(Map.of("title", message.title(), "body", message.body(), "url", message.url()));
        for (PushSubscription device : subscriptionRepository.findByUser_IdOrderByCreatedAtDesc(userId)) {
            try {
                int status = webPushClient.send(device.getEndpoint(), device.getP256dh(), device.getAuth(), payload);
                if (status == 404 || status == 410) {
                    // The browser dropped this subscription (uninstalled, permission withdrawn, expired)
                    subscriptionRepository.deleteByEndpoint(device.getEndpoint());
                } else if (status >= 400) {
                    log.warn("Push service answered {} for a device of user {}", status, userId);
                }
            } catch (RestClientException | IllegalStateException ex) {
                log.warn("Push to a device of user {} not delivered: {}", userId, ex.getMessage());
            }
        }
    }

    private void checkEndpoint(String endpoint) {
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException ex) {
            throw invalidEndpoint();
        }
        String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getUserInfo() != null
                || properties.allowedHosts().stream().noneMatch(allowed -> hostMatches(host, allowed))) {
            throw invalidEndpoint();
        }
    }

    private static boolean hostMatches(String host, String allowed) {
        String pattern = allowed.trim().toLowerCase(Locale.ROOT);
        if (pattern.startsWith("*.")) {
            return host.endsWith(pattern.substring(1));
        }
        return host.equals(pattern);
    }

    private static InvalidRequestException invalidEndpoint() {
        return new InvalidRequestException("The endpoint is not a known push service",
                Map.of("endpoint", "Must be an https address of a known push service"));
    }

    private static void checkKeys(PushSubscriptionRequestDTO.Keys keys) {
        try {
            WebPushCrypto.publicKey(keys.p256dh());
            if (WebPushCrypto.decode(keys.auth()).length != 16) {
                throw new GeneralSecurityException("auth is not 16 bytes");
            }
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new InvalidRequestException("The subscription keys are not valid",
                    Map.of("keys", "p256dh must be a P-256 public key and auth 16 bytes, both base64url"));
        }
    }
}
