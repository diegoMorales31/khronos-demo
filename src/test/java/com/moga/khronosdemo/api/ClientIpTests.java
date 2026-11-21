package com.moga.khronosdemo.api;

import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The login limits behind a reverse proxy. MockMvc skips Tomcat, which is what reads X-Forwarded-For
 * (server.forward-headers-strategy=native), so these requests go to a real port. They come from
 * 127.0.0.1, a private address, and so are taken as coming through a trusted proxy.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ClientIpTests extends IntegrationTest {

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Test
    void eachClientBehindTheProxyHasItsOwnLimitAndCannotChooseItsAddress() throws Exception {
        createOwner("proxied");
        String client = "203.0.113.70";

        for (int i = 0; i < 5; i++) {
            assertEquals(401, login("proxied", "wrong-password", client));
        }
        assertEquals(429, login("proxied", PASSWORD, client));

        // An address the client writes in front of the proxy's is ignored: the proxy's last one counts
        assertEquals(429, login("proxied", PASSWORD, "198.51.100.99, " + client));
        // Another client behind the same proxy is not affected
        assertEquals(200, login("proxied", PASSWORD, "203.0.113.71"));
    }

    private int login(String username, String password, String forwardedFor) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", forwardedFor)
                .POST(HttpRequest.BodyPublishers.ofString("""
                        { "username": "%s", "password": "%s" }
                        """.formatted(username, password)))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
