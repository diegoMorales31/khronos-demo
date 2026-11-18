package com.moga.khronosdemo.api;

import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.Event;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.entities.WorkerEvent;
import com.moga.khronosdemo.entities.Zone;
import com.moga.khronosdemo.entities.enums.Role;
import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The per-establishment listings that save the front end one request per event or per worker.
 */
class EstablishmentOverviewTests extends IntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2030, 5, 10);

    private User owner;
    private Establishment venue;
    private Worker worker;
    private Worker colleague;
    private Worker outsider;
    private WorkerEvent today;
    private WorkerEvent todayColleague;
    private WorkerEvent tomorrow;

    @BeforeEach
    void setUp() {
        owner = createOwner("owner");
        venue = createEstablishment(owner);
        Zone hall = createZone(venue, "Hall");
        worker = createWorker(venue, "worker");
        colleague = createWorker(venue, "colleague");
        createShift(worker, hall, DAY, "18:00", "23:00");
        createShift(colleague, hall, DAY, "18:00", "23:00");
        createShift(worker, hall, DAY.plusDays(1), "18:00", "23:00");
        Event dinner = createEvent(hall, DAY.atTime(19, 0));
        today = assign(worker, dinner);
        todayColleague = assign(colleague, dinner);
        tomorrow = assign(worker, createEvent(hall, DAY.plusDays(1).atTime(20, 0)));

        Establishment otherVenue = createEstablishment(createOwner("other-owner"));
        Zone otherZone = createZone(otherVenue, "Hall");
        outsider = createWorker(otherVenue, "outsider");
        createShift(outsider, otherZone, DAY, "18:00", "23:00");
        assign(outsider, createEvent(otherZone, DAY.atTime(19, 0)));
    }

    @Test
    void listsTheAssignmentsOfAllTheEstablishmentsEvents() throws Exception {
        mockMvc.perform(getJson("/api/worker-events/establishment/{id}", venue.getId()).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(
                        today.getId().intValue(), todayColleague.getId().intValue(), tomorrow.getId().intValue())))
                .andExpect(jsonPath("$[0].workerId").value(worker.getUserId()))
                .andExpect(jsonPath("$[0].eventId").value(today.getEvent().getId()));

        mockMvc.perform(getJson("/api/worker-events/establishment/{id}?from=2030-05-11&to=2030-05-11", venue.getId())
                        .with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(tomorrow.getId().intValue())));
    }

    @Test
    void aWorkerOnlyGetsTheirOwnAssignments() throws Exception {
        mockMvc.perform(getJson("/api/worker-events/establishment/{id}", venue.getId()).with(as(worker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(today.getId().intValue(), tomorrow.getId().intValue())))
                .andExpect(jsonPath("$[*].workerId", everyItem(is(worker.getUserId().intValue()))));
        mockMvc.perform(getJson("/api/worker-events/establishment/{id}", venue.getId()).with(as(colleague)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(todayColleague.getId().intValue())));

        // The date filters still apply
        mockMvc.perform(getJson("/api/worker-events/establishment/{id}?from=2030-05-11&to=2030-05-11", venue.getId())
                        .with(as(worker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(tomorrow.getId().intValue())));
        mockMvc.perform(getJson("/api/worker-events/establishment/{id}?from=2030-05-11&to=2030-05-11", venue.getId())
                        .with(as(colleague)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // The owner and an admin still get the whole staff's
        for (RequestPostProcessor caller : List.of(as(owner), as(createAdmin("admin")))) {
            mockMvc.perform(getJson("/api/worker-events/establishment/{id}", venue.getId()).with(caller))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].id", contains(
                            today.getId().intValue(), todayColleague.getId().intValue(), tomorrow.getId().intValue())));
        }
    }

    @Test
    void assignmentsAreVisibleToTheEstablishmentsMembersOnly() throws Exception {
        mockMvc.perform(getJson("/api/worker-events/establishment/{id}", venue.getId()).with(as(colleague)))
                .andExpect(status().isOk());
        mockMvc.perform(getJson("/api/worker-events/establishment/{id}", venue.getId()).with(as(outsider)))
                .andExpect(status().isForbidden());
        mockMvc.perform(getJson("/api/worker-events/establishment/{id}", venue.getId())
                        .with(as(createOwner("third-owner"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listsTheAccountsOfTheEstablishmentsWorkers() throws Exception {
        // Neither the owner nor an unlinked account belongs to the staff list
        createUser("unlinked", Role.WORKER);

        mockMvc.perform(getJson("/api/users/establishment/{id}", venue.getId()).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].username", containsInAnyOrder("worker", "colleague")))
                .andExpect(jsonPath("$[0].email").value("worker@example.test"))
                .andExpect(jsonPath("$[0].password").doesNotExist());

        mockMvc.perform(getJson("/api/users/establishment/{id}", venue.getId()).with(as(createAdmin("admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void workerAccountsAreVisibleToTheOwnerOnly() throws Exception {
        mockMvc.perform(getJson("/api/users/establishment/{id}", venue.getId()).with(as(worker)))
                .andExpect(status().isForbidden());
        mockMvc.perform(getJson("/api/users/establishment/{id}", venue.getId()).with(as(createOwner("third-owner"))))
                .andExpect(status().isForbidden());
    }
}
