package com.moga.khronosdemo.api;

import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.Event;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.entities.WorkerEvent;
import com.moga.khronosdemo.entities.WorkerShift;
import com.moga.khronosdemo.entities.Zone;
import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A worker sees their own schedule and their venue, but nothing about other workers' accounts
 * or other venues.
 */
class WorkerVisibilityTests extends IntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2030, 5, 10);

    private User owner;
    private Establishment venue;
    private Establishment otherVenue;
    private Worker worker;
    private Worker colleague;
    private Worker outsider;
    private WorkerShift colleagueShift;
    private WorkerShift outsiderShift;
    private WorkerEvent outsiderAssignment;

    @BeforeEach
    void setUp() {
        owner = createOwner("owner");
        venue = createEstablishment(owner);
        otherVenue = createEstablishment(createOwner("other-owner"));
        Zone zone = createZone(venue, "Bar");
        Zone otherZone = createZone(otherVenue, "Bar");
        worker = createWorker(venue, "worker");
        colleague = createWorker(venue, "colleague");
        outsider = createWorker(otherVenue, "outsider");
        createShift(worker, zone, DAY, "09:00", "13:00");
        colleagueShift = createShift(colleague, zone, DAY, "18:00", "23:00");
        outsiderShift = createShift(outsider, otherZone, DAY, "18:00", "23:00");
        Event otherEvent = createEvent(otherZone, DAY.atTime(19, 0));
        outsiderAssignment = assign(outsider, otherEvent);
    }

    @Test
    void workerSeesTheirOwnScheduleAndVenue() throws Exception {
        mockMvc.perform(getJson("/api/worker-shifts/worker/{id}", worker.getUserId()).with(as(worker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(getJson("/api/establishments/{id}", venue.getId()).with(as(worker)))
                .andExpect(status().isOk());
        mockMvc.perform(getJson("/api/users/me").with(as(worker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("worker"));
    }

    @Test
    void workerSeesWhoRunsTheVenueButNotTheirContactDetails() throws Exception {
        owner.setPhoneNumber("+34 600 000 000");
        userRepository.save(owner);

        mockMvc.perform(getJson("/api/establishments/{id}", venue.getId()).with(as(worker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value(owner.getId()))
                .andExpect(jsonPath("$.ownerName").value("First owner Last owner"))
                .andExpect(jsonPath("$.ownerEmail").value(nullValue()))
                .andExpect(jsonPath("$.ownerPhone").value(nullValue()));

        mockMvc.perform(getJson("/api/establishments/{id}", venue.getId()).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerEmail").value("owner@example.test"))
                .andExpect(jsonPath("$.ownerPhone").value("+34 600 000 000"));
        mockMvc.perform(getJson("/api/establishments/owner/{id}", owner.getId()).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ownerEmail").value("owner@example.test"));
    }

    @Test
    void workerCannotSeeOtherVenuesOrOtherPeoplesData() throws Exception {
        expectForbidden(getJson("/api/establishments/{id}", otherVenue.getId()));
        expectForbidden(getJson("/api/events/establishment/{id}", otherVenue.getId()));
        expectForbidden(getJson("/api/worker-shifts/{id}", outsiderShift.getId()));
        expectForbidden(getJson("/api/worker-shifts/{id}", colleagueShift.getId()));
        expectForbidden(getJson("/api/worker-shifts/worker/{id}", colleague.getUserId()));
        expectForbidden(getJson("/api/worker-events/{id}", outsiderAssignment.getId()));
        expectForbidden(getJson("/api/users/{id}", colleague.getUserId()));
        expectForbidden(getJson("/api/users/{id}", owner.getId()));
        expectForbidden(getJson("/api/workers/establishment/{id}", venue.getId()));
        expectForbidden(getJson("/api/worker-shifts/owner/{id}", owner.getId()));
    }

    @Test
    void workerCannotChangeTheSchedule() throws Exception {
        expectForbidden(postJson("/api/worker-shifts", """
                [ { "workerId": %d, "zoneId": %d, "date": "2030-05-11", "startTime": "10:00", "endTime": "12:00" } ]
                """.formatted(worker.getUserId(), colleagueShift.getZone().getId())));
        expectForbidden(deleteJson("/api/worker-shifts/{id}", colleagueShift.getId()));
        expectForbidden(deleteJson("/api/workers/deactivate/{id}", colleague.getUserId()));
    }

    private void expectForbidden(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request.with(as(worker)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }
}
