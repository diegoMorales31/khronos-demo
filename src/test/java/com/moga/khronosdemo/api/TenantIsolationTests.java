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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Owner A must get 403 for everything that belongs to owner B, and nothing of B's may change.
 */
class TenantIsolationTests extends IntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2030, 5, 10);

    private User ownerA;
    private User ownerB;
    private Establishment venueA;
    private Establishment venueB;
    private Zone zoneA;
    private Zone zoneB;
    private Worker workerA;
    private Worker workerB;
    private WorkerShift shiftB;
    private Event eventB;
    private WorkerEvent assignmentB;

    @BeforeEach
    void setUp() {
        ownerA = createOwner("owner-a");
        ownerB = createOwner("owner-b");
        venueA = createEstablishment(ownerA);
        venueB = createEstablishment(ownerB);
        zoneA = createZone(venueA, "Terrace");
        zoneB = createZone(venueB, "Kitchen");
        workerA = createWorker(venueA, "worker-a");
        workerB = createWorker(venueB, "worker-b");
        shiftB = createShift(workerB, zoneB, DAY, "18:00", "23:00");
        eventB = createEvent(zoneB, DAY.atTime(19, 0));
        assignmentB = assign(workerB, eventB);
    }

    @Test
    void ownerCanStillManageTheirOwnVenue() throws Exception {
        mockMvc.perform(getJson("/api/establishments/{id}", venueA.getId()).with(as(ownerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value(ownerA.getId()));
        mockMvc.perform(getJson("/api/zones/{id}", zoneA.getId()).with(as(ownerA)))
                .andExpect(status().isOk());
        mockMvc.perform(getJson("/api/workers/{id}", workerA.getUserId()).with(as(ownerA)))
                .andExpect(status().isOk());
    }

    @Test
    void ownerCannotReadOrChangeAnotherOwnersEstablishment() throws Exception {
        expectForbidden(getJson("/api/establishments/{id}", venueB.getId()));
        expectForbidden(putJson("/api/establishments/{id}", """
                { "establishmentName": "Taken", "establishmentAddress": "1 Hijack Road", "ownerId": %d }
                """.formatted(ownerA.getId()), venueB.getId()));
        expectForbidden(getJson("/api/establishments/owner/{id}", ownerB.getId()));

        Establishment unchanged = establishmentRepository.findById(venueB.getId()).orElseThrow();
        assertTrue(unchanged.getEstablishmentName().startsWith("Venue "));
    }

    @Test
    void ownerCannotReadOrChangeAnotherOwnersZone() throws Exception {
        expectForbidden(getJson("/api/zones/{id}", zoneB.getId()));
        expectForbidden(getJson("/api/zones/establishment/{id}", venueB.getId()));
        expectForbidden(postJson("/api/zones", """
                { "name": "Intruder", "establishmentId": %d }
                """.formatted(venueB.getId())));
        expectForbidden(putJson("/api/zones/{id}", """
                { "name": "Renamed", "establishmentId": %d }
                """.formatted(venueB.getId()), zoneB.getId()));
        expectForbidden(deleteJson("/api/zones/{id}", zoneB.getId()));

        assertTrue(zoneRepository.existsById(zoneB.getId()));
    }

    @Test
    void ownerCannotReadOrChangeAnotherOwnersWorker() throws Exception {
        expectForbidden(getJson("/api/workers/{id}", workerB.getUserId()));
        expectForbidden(getJson("/api/workers/establishment/{id}", venueB.getId()));
        expectForbidden(getJson("/api/users/{id}", workerB.getUserId()));
        expectForbidden(putJson("/api/workers/{id}", """
                { "userId": %d, "establishmentId": %d, "isActive": false }
                """.formatted(workerB.getUserId(), venueA.getId()), workerB.getUserId()));
        expectForbidden(putJson("/api/users/{id}", """
                { "username": "stolen", "firstName": "S", "lastName": "S", "email": "stolen@example.test" }
                """, workerB.getUserId()));
        expectForbidden(deleteJson("/api/workers/deactivate/{id}", workerB.getUserId()));

        Worker unchanged = workerRepository.findById(workerB.getUserId()).orElseThrow();
        assertTrue(unchanged.getActive());
    }

    @Test
    void ownerCannotReadOrChangeAnotherOwnersShift() throws Exception {
        expectForbidden(getJson("/api/worker-shifts/{id}", shiftB.getId()));
        expectForbidden(getJson("/api/worker-shifts/worker/{id}", workerB.getUserId()));
        expectForbidden(getJson("/api/worker-shifts/zone/{id}", zoneB.getId()));
        expectForbidden(getJson("/api/worker-shifts/owner/{id}", ownerB.getId()));
        expectForbidden(postJson("/api/worker-shifts", """
                [ { "workerId": %d, "zoneId": %d, "date": "2030-05-11", "startTime": "10:00", "endTime": "12:00" } ]
                """.formatted(workerB.getUserId(), zoneB.getId())));
        expectForbidden(putJson("/api/worker-shifts/{id}", """
                { "workerId": %d, "zoneId": %d, "date": "2030-05-10", "startTime": "10:00", "endTime": "12:00" }
                """.formatted(workerB.getUserId(), zoneB.getId()), shiftB.getId()));
        expectForbidden(deleteJson("/api/worker-shifts/{id}", shiftB.getId()));

        assertTrue(workerShiftRepository.existsById(shiftB.getId()));
    }

    @Test
    void ownerCannotReadOrChangeAnotherOwnersEvent() throws Exception {
        expectForbidden(getJson("/api/events/{id}", eventB.getId()));
        expectForbidden(getJson("/api/events/zone/{id}", zoneB.getId()));
        expectForbidden(getJson("/api/events/establishment/{id}", venueB.getId()));
        expectForbidden(postJson("/api/events", """
                [ { "title": "Intruder", "dateTime": "2030-05-10T20:00:00", "zoneId": %d, "peopleNumber": 10 } ]
                """.formatted(zoneB.getId())));
        expectForbidden(putJson("/api/events/{id}", """
                { "title": "Moved", "dateTime": "2030-05-10T20:00:00", "zoneId": %d, "peopleNumber": 10 }
                """.formatted(zoneA.getId()), eventB.getId()));
        expectForbidden(deleteJson("/api/events/{id}", eventB.getId()));

        assertTrue(eventRepository.existsById(eventB.getId()));
    }

    @Test
    void ownerCannotReadOrChangeAnotherOwnersAssignment() throws Exception {
        expectForbidden(getJson("/api/worker-events/{id}", assignmentB.getId()));
        expectForbidden(getJson("/api/worker-events/worker/{id}", workerB.getUserId()));
        expectForbidden(getJson("/api/worker-events/event/{id}", eventB.getId()));
        expectForbidden(postJson("/api/worker-events", """
                [ { "workerId": %d, "eventId": %d } ]
                """.formatted(workerA.getUserId(), eventB.getId())));
        expectForbidden(deleteJson("/api/worker-events/{id}", assignmentB.getId()));

        assertTrue(workerEventRepository.existsById(assignmentB.getId()));
    }

    private void expectForbidden(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request.with(as(ownerA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("You do not have permission to perform this action"));
    }
}
