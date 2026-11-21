package com.moga.khronosdemo.api;

import com.jayway.jsonpath.JsonPath;
import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.Event;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.entities.InboxNotification;
import com.moga.khronosdemo.entities.Zone;
import com.moga.khronosdemo.entities.enums.NotificationType;
import com.moga.khronosdemo.repositories.InboxNotificationRepository;
import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shifts and events assigned to a worker in one request reach them as a single notification in their
 * in-app list (the push carries the same). The notice is saved in the request's transaction, so a request
 * that fails notifies nobody.
 */
class AssignmentNotificationTests extends IntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2030, 5, 10);

    @Autowired
    private InboxNotificationRepository inboxNotificationRepository;

    private User owner;
    private Zone hall;
    private Worker worker;
    private Worker colleague;

    @BeforeEach
    void setUp() {
        owner = createOwner("owner");
        Establishment venue = createEstablishment(owner);
        hall = createZone(venue, "Hall");
        worker = createWorker(venue, "worker");
        colleague = createWorker(venue, "colleague");
    }

    @Test
    void severalShiftsForOneWorkerMakeOneNotification() throws Exception {
        mockMvc.perform(postJson("/api/worker-shifts", """
                        [
                          { "workerId": %1$d, "zoneId": %2$d, "date": "2030-05-11", "startTime": "09:00", "endTime": "13:00" },
                          { "workerId": %1$d, "zoneId": %2$d, "date": "2030-05-10", "startTime": "09:00", "endTime": "13:00" },
                          { "workerId": %1$d, "zoneId": %2$d, "date": "2030-05-12", "startTime": "18:00", "endTime": "02:00" }
                        ]
                        """.formatted(worker.getUserId(), hall.getId())).with(as(owner)))
                .andExpect(status().isCreated());

        assertAssigned(worker, 3, 0);
        assertEquals(1, inboxNotificationRepository.count());
    }

    @Test
    void shiftsForTwoWorkersMakeOneNotificationEach() throws Exception {
        mockMvc.perform(postJson("/api/worker-shifts", """
                        [
                          { "workerId": %1$d, "zoneId": %3$d, "date": "2030-05-10", "startTime": "09:00", "endTime": "13:00" },
                          { "workerId": %2$d, "zoneId": %3$d, "date": "2030-05-10", "startTime": "09:00", "endTime": "17:00" },
                          { "workerId": %1$d, "zoneId": %3$d, "date": "2030-05-10", "startTime": "13:00", "endTime": "17:00" }
                        ]
                        """.formatted(worker.getUserId(), colleague.getUserId(), hall.getId())).with(as(owner)))
                .andExpect(status().isCreated());

        assertAssigned(colleague, 1, 0);
        assertAssigned(worker, 2, 0);
    }

    @Test
    void aShiftBatchThatFailsHalfwayNotifiesNobody() throws Exception {
        createShift(worker, hall, DAY, "18:00", "23:00");

        mockMvc.perform(postJson("/api/worker-shifts", """
                        [
                          { "workerId": %1$d, "zoneId": %3$d, "date": "2030-05-10", "startTime": "09:00", "endTime": "12:00" },
                          { "workerId": %2$d, "zoneId": %3$d, "date": "2030-05-10", "startTime": "09:00", "endTime": "12:00" },
                          { "workerId": %1$d, "zoneId": %3$d, "date": "2030-05-10", "startTime": "20:00", "endTime": "21:00" }
                        ]
                        """.formatted(worker.getUserId(), colleague.getUserId(), hall.getId())).with(as(owner)))
                .andExpect(status().isConflict());

        assertEquals(0, inboxNotificationRepository.count());
    }

    @Test
    void severalEventsForOneWorkerMakeOneNotification() throws Exception {
        createShift(worker, hall, DAY, "18:00", "23:00");
        Event dinner = createEvent(hall, DAY.atTime(19, 0));
        Event cocktails = createEvent(hall, DAY.atTime(22, 30));

        mockMvc.perform(postJson("/api/worker-events", """
                        [ { "workerId": %1$d, "eventId": %3$d }, { "workerId": %1$d, "eventId": %2$d } ]
                        """.formatted(worker.getUserId(), dinner.getId(), cocktails.getId())).with(as(owner)))
                .andExpect(status().isCreated());

        assertAssigned(worker, 0, 2);
        assertEquals(1, inboxNotificationRepository.count());
    }

    @Test
    void eventsForTwoWorkersMakeOneNotificationEach() throws Exception {
        createShift(worker, hall, DAY, "18:00", "23:00");
        createShift(colleague, hall, DAY, "18:00", "23:00");
        Event dinner = createEvent(hall, DAY.atTime(19, 0));
        Event cocktails = createEvent(hall, DAY.atTime(22, 30));

        mockMvc.perform(postJson("/api/worker-events", """
                        [
                          { "workerId": %1$d, "eventId": %3$d },
                          { "workerId": %2$d, "eventId": %3$d },
                          { "workerId": %1$d, "eventId": %4$d }
                        ]
                        """.formatted(worker.getUserId(), colleague.getUserId(), dinner.getId(), cocktails.getId()))
                        .with(as(owner)))
                .andExpect(status().isCreated());

        assertAssigned(colleague, 0, 1);
        assertAssigned(worker, 0, 2);
    }

    @Test
    void anEventBatchThatFailsHalfwayNotifiesNobody() throws Exception {
        createShift(worker, hall, DAY, "18:00", "23:00");
        Event dinner = createEvent(hall, DAY.atTime(19, 0));

        mockMvc.perform(postJson("/api/worker-events", """
                        [ { "workerId": %1$d, "eventId": %2$d }, { "workerId": %1$d, "eventId": %2$d } ]
                        """.formatted(worker.getUserId(), dinner.getId())).with(as(owner)))
                .andExpect(status().isConflict());

        assertEquals(0, inboxNotificationRepository.count());
    }

    @Test
    void aShiftAndTheEventsItCoversGoInOneNotification() throws Exception {
        Event dinner = createEvent(hall, DAY.atTime(19, 0));
        Event cocktails = createEvent(hall, DAY.atTime(22, 30));

        mockMvc.perform(postJson("/api/worker-shifts/with-events", """
                        {
                          "shifts": [ { "workerId": %1$d, "zoneId": %2$d, "date": "2030-05-10", "startTime": "18:00", "endTime": "23:00" } ],
                          "assignments": [ { "workerId": %1$d, "eventId": %3$d }, { "workerId": %1$d, "eventId": %4$d } ]
                        }
                        """.formatted(worker.getUserId(), hall.getId(), dinner.getId(), cocktails.getId())).with(as(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shifts", hasSize(1)))
                .andExpect(jsonPath("$.assignments", hasSize(2)))
                .andExpect(jsonPath("$.assignments[0].eventId").value(dinner.getId()));

        assertEquals(1, workerShiftRepository.count());
        assertEquals(2, workerEventRepository.count());
        assertAssigned(worker, 1, 2);
        assertEquals(1, inboxNotificationRepository.count());
    }

    @Test
    void shiftsWithEventsForTwoWorkersMakeOneNotificationEach() throws Exception {
        Event dinner = createEvent(hall, DAY.atTime(19, 0));

        mockMvc.perform(postJson("/api/worker-shifts/with-events", """
                        {
                          "shifts": [
                            { "workerId": %1$d, "zoneId": %3$d, "date": "2030-05-10", "startTime": "18:00", "endTime": "23:00" },
                            { "workerId": %2$d, "zoneId": %3$d, "date": "2030-05-10", "startTime": "18:00", "endTime": "23:00" }
                          ],
                          "assignments": [ { "workerId": %1$d, "eventId": %4$d }, { "workerId": %2$d, "eventId": %4$d } ]
                        }
                        """.formatted(worker.getUserId(), colleague.getUserId(), hall.getId(), dinner.getId())).with(as(owner)))
                .andExpect(status().isCreated());

        assertAssigned(worker, 1, 1);
        assertAssigned(colleague, 1, 1);
    }

    @Test
    void anEventTheNewShiftDoesNotCoverSavesNothingAndNotifiesNobody() throws Exception {
        Event late = createEvent(hall, DAY.atTime(23, 30));

        mockMvc.perform(postJson("/api/worker-shifts/with-events", """
                        {
                          "shifts": [ { "workerId": %1$d, "zoneId": %2$d, "date": "2030-05-10", "startTime": "18:00", "endTime": "23:00" } ],
                          "assignments": [ { "workerId": %1$d, "eventId": %3$d } ]
                        }
                        """.formatted(worker.getUserId(), hall.getId(), late.getId())).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(startsWith("Assignment [0]: ")));

        assertEquals(0, workerShiftRepository.count());
        assertEquals(0, workerEventRepository.count());
        assertEquals(0, inboxNotificationRepository.count());
    }

    @Test
    void shiftsWithNoEventsAreJustShifts() throws Exception {
        mockMvc.perform(postJson("/api/worker-shifts/with-events", """
                        { "shifts": [ { "workerId": %d, "zoneId": %d, "date": "2030-05-10", "startTime": "18:00", "endTime": "23:00" } ], "assignments": [] }
                        """.formatted(worker.getUserId(), hall.getId())).with(as(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assignments", hasSize(0)));

        assertAssigned(worker, 1, 0);
    }

    @Test
    void anotherOwnersEventCannotBeAssignedWithTheShift() throws Exception {
        User stranger = createOwner("stranger");
        Event theirs = createEvent(createZone(createEstablishment(stranger), "Their hall"), DAY.atTime(19, 0));

        mockMvc.perform(postJson("/api/worker-shifts/with-events", """
                        {
                          "shifts": [ { "workerId": %1$d, "zoneId": %2$d, "date": "2030-05-10", "startTime": "18:00", "endTime": "23:00" } ],
                          "assignments": [ { "workerId": %1$d, "eventId": %3$d } ]
                        }
                        """.formatted(worker.getUserId(), hall.getId(), theirs.getId())).with(as(owner)))
                .andExpect(status().isForbidden());

        assertEquals(0, workerShiftRepository.count());
    }

    // The worker has exactly one notice, an assignment with this many shifts and events
    private void assertAssigned(Worker recipient, int shifts, int events) {
        List<InboxNotification> notices = inboxNotificationRepository.findLatest(recipient.getUserId(), Limit.of(10));
        assertEquals(1, notices.size());
        assertEquals(NotificationType.ASSIGNED, notices.get(0).getType());
        assertEquals(shifts, JsonPath.<List<?>>read(notices.get(0).getData(), "$.shifts").size());
        assertEquals(events, JsonPath.<List<?>>read(notices.get(0).getData(), "$.events").size());
    }
}
