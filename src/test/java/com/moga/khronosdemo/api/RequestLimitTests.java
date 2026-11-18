package com.moga.khronosdemo.api;

import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.entities.Zone;
import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RequestLimitTests extends IntegrationTest {

    private static final int MAX_BATCH = 200;
    // 37 two-byte characters: under 72 characters but over BCrypt's 72 bytes
    private static final String PASSWORD_OVER_72_BYTES = "ñ".repeat(37);

    private User owner;
    private Zone zone;
    private Worker worker;

    @BeforeEach
    void setUp() {
        owner = createOwner("owner");
        Establishment venue = createEstablishment(owner);
        zone = createZone(venue, "Hall");
        worker = createWorker(venue, "worker");
    }

    @Test
    void acceptsABatchUpToTheLimitAndRejectsOneOverIt() throws Exception {
        mockMvc.perform(postJson("/api/events", events(MAX_BATCH)).with(as(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(MAX_BATCH)));

        mockMvc.perform(postJson("/api/events", events(MAX_BATCH + 1)).with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.request").value("must not contain more than 200 items"));

        assertEquals(MAX_BATCH, eventRepository.count());
    }

    @Test
    void rejectsOversizedShiftAndAssignmentBatches() throws Exception {
        String shifts = IntStream.range(0, MAX_BATCH + 1)
                .mapToObj(i -> """
                        { "workerId": %d, "zoneId": %d, "date": "2030-05-10", "startTime": "09:00", "endTime": "10:00" }
                        """.formatted(worker.getUserId(), zone.getId()))
                .collect(Collectors.joining(",", "[", "]"));
        mockMvc.perform(postJson("/api/worker-shifts", shifts).with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.request").value("must not contain more than 200 items"));

        String assignments = IntStream.range(0, MAX_BATCH + 1)
                .mapToObj(i -> "{ \"workerId\": %d, \"eventId\": %d }".formatted(worker.getUserId(), i + 1))
                .collect(Collectors.joining(",", "[", "]"));
        mockMvc.perform(postJson("/api/worker-events", assignments).with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.request").value("must not contain more than 200 items"));

        // The size is checked before the permission rules, which would otherwise run per item
        mockMvc.perform(postJson("/api/worker-shifts", shifts).with(as(worker)))
                .andExpect(status().isBadRequest());

        assertEquals(0, workerShiftRepository.count());
    }

    @Test
    void passwordsAreLimitedTo72BytesWhereverTheyAreSent() throws Exception {
        String exactly72Bytes = "a".repeat(72);
        mockMvc.perform(postJson("/api/users/create/worker", user("long-ok", exactly72Bytes)).with(as(owner)))
                .andExpect(status().isCreated());

        mockMvc.perform(postJson("/api/users/create/worker", user("too-long", PASSWORD_OVER_72_BYTES)).with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").value("must be at most 72 bytes long"));
        assertFalse(userRepository.existsByUsername("too-long"));

        mockMvc.perform(postJson("/api/workers/with-user", """
                        { "establishmentId": %d, "user": %s }
                        """.formatted(zone.getEstablishment().getId(), user("too-long", PASSWORD_OVER_72_BYTES)))
                        .with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['user.password']").value("must be at most 72 bytes long"));

        mockMvc.perform(postJson("/api/auth/login", """
                        { "username": "owner", "password": "%s" }
                        """.formatted(PASSWORD_OVER_72_BYTES)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").value("must be at most 72 bytes long"));

        mockMvc.perform(putJson("/api/users/{id}/password", """
                        { "currentPassword": "%s", "newPassword": "%s" }
                        """.formatted(PASSWORD, PASSWORD_OVER_72_BYTES), owner.getId()).with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newPassword").value("must be at most 72 bytes long"));

        mockMvc.perform(putJson("/api/users/{id}/password/reset", """
                        { "newPassword": "%s" }
                        """.formatted(PASSWORD_OVER_72_BYTES), worker.getUserId()).with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newPassword").value("must be at most 72 bytes long"));
    }

    private String events(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> """
                        { "title": "Event %d", "dateTime": "2030-05-10T19:00:00", "zoneId": %d, "peopleNumber": 10 }
                        """.formatted(i, zone.getId()))
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String user(String username, String password) {
        return """
                { "username": "%1$s", "password": "%2$s", "firstName": "First", "lastName": "Last",
                  "email": "%1$s@example.test" }
                """.formatted(username, password);
    }
}
