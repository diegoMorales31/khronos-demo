package com.moga.khronosdemo.notifications;

import com.moga.khronosdemo.config.PushProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;

/**
 * Hands one encrypted message to the push service behind a device's endpoint and returns its HTTP
 * status: 201 accepted; 404 or 410 the device is gone for good. Throws RestClientException when the
 * push service can't be reached. Who receives what is decided in PushServiceImpl.
 */
@Component
public class WebPushClient {

    private final PushProperties properties;
    private final RestClient restClient;
    private final Clock clock;

    public WebPushClient(PushProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        // Without timeouts a slow push service would hold a notification thread indefinitely
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build());
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public int send(String endpoint, String p256dh, String auth, byte[] payload) {
        URI uri = URI.create(endpoint);
        byte[] body = WebPushCrypto.encrypt(payload, p256dh, auth);
        return restClient.post()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, WebPushCrypto.vapidAuthorization(uri, properties.subject(),
                        properties.vapidPublicKey(), properties.vapidPrivateKey(), clock.instant()))
                .header("TTL", Long.toString(properties.ttl().toSeconds()))
                .header("Urgency", "normal")
                .header(HttpHeaders.CONTENT_ENCODING, "aes128gcm")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body)
                .exchange((request, response) -> response.getStatusCode().value());
    }
}
