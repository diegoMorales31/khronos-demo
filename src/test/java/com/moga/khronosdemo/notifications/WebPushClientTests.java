package com.moga.khronosdemo.notifications;

import com.moga.khronosdemo.config.PushProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a push service receives, against a local server standing in for one. */
class WebPushClientTests {

    private static final String UA_PUBLIC = "BBQ8TGzcOPkE6Ue6b3Q_VQT7HcAPxQcj7lxz1YkcbySWOCYosmLLm5BtBfV0kQ-KvvxApXr2JFCbdygZ_RRO1-E";
    private static final String AUTH = "1MFE0QgKg3vX4AJBXQjXYA";
    private static final String VAPID_PUBLIC = "BFfioXriDLXRtXOUjFkHaVKNk-2Z0qX_IvsJzFF8HoyfMH8jeyl1R_GuCEapxHukXU61-AZt_OOSEPg-i5257Gw";
    private static final String VAPID_PRIVATE = "4_mRhhgln0JUXxD5sUP54VO4cn9mf8pSe_r8NuZndNE";

    private HttpServer server;
    private final AtomicInteger answer = new AtomicInteger(201);
    private final AtomicReference<com.sun.net.httpserver.Headers> received = new AtomicReference<>();
    private final AtomicInteger bodyLength = new AtomicInteger();
    private WebPushClient client;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/push/", exchange -> {
            received.set(exchange.getRequestHeaders());
            bodyLength.set(exchange.getRequestBody().readAllBytes().length);
            exchange.sendResponseHeaders(answer.get(), -1);
            exchange.close();
        });
        server.start();
        PushProperties properties = new PushProperties(VAPID_PUBLIC, VAPID_PRIVATE, "mailto:ops@example.test",
                Duration.ofDays(1), Duration.ofSeconds(2), Duration.ofSeconds(2), List.of("fcm.googleapis.com"));
        client = new WebPushClient(properties, Clock.fixed(Instant.parse("2030-05-10T12:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private String endpoint() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/push/device-1";
    }

    @Test
    void sendsTheEncryptedMessageWithTheVapidHeaders() {
        int status = client.send(endpoint(), UA_PUBLIC, AUTH, "{\"title\":\"New shift\"}".getBytes());

        assertEquals(201, status);
        assertEquals("aes128gcm", received.get().getFirst("Content-Encoding"));
        assertEquals("86400", received.get().getFirst("TTL"));
        assertTrue(received.get().getFirst("Authorization").startsWith("vapid t="));
        assertTrue(received.get().getFirst("Authorization").endsWith(", k=" + VAPID_PUBLIC));
        // salt, record size, key length and key (86 bytes), then the 21-byte content, its delimiter and the 16-byte tag
        assertEquals(86 + 21 + 1 + 16, bodyLength.get());
    }

    @Test
    void returnsTheStatusOfADeviceThatIsGone() {
        answer.set(410);

        assertEquals(410, client.send(endpoint(), UA_PUBLIC, AUTH, new byte[]{1}));
    }
}
