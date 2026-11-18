package com.moga.khronosdemo.api;

import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CorsTests {

    private static final String FRONT = "https://front.example.test";

    @Nested
    class WithoutAllowedOrigins extends IntegrationTest {

        @Test
        void crossOriginRequestsGetNoCorsHeaders() throws Exception {
            mockMvc.perform(options("/api/users/me")
                            .header(HttpHeaders.ORIGIN, FRONT)
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        }
    }

    @Nested
    @TestPropertySource(properties = "app.cors.allowed-origins=" + FRONT + ", https://admin.example.test")
    class WithAllowedOrigins extends IntegrationTest {

        @Test
        void answersThePreflightOfAnAllowedOrigin() throws Exception {
            mockMvc.perform(options("/api/worker-shifts")
                            .header(HttpHeaders.ORIGIN, FRONT)
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization, content-type"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONT))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("POST")));
        }

        @Test
        void rejectsThePreflightOfAnyOtherOrigin() throws Exception {
            mockMvc.perform(options("/api/worker-shifts")
                            .header(HttpHeaders.ORIGIN, "https://evil.example.test")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        }

        @Test
        void exposesRetryAfterOnActualRequests() throws Exception {
            mockMvc.perform(getJson("/api/users/me").header(HttpHeaders.ORIGIN, "https://admin.example.test")
                            .with(as(createOwner("owner"))))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://admin.example.test"))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, containsString("Retry-After")));
        }
    }
}
