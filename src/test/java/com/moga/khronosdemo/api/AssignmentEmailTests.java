package com.moga.khronosdemo.api;

import com.moga.khronosdemo.dtos.request.EmailRequestDTO;
import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.Event;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.entities.Zone;
import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shifts and events assigned to a worker in one request reach them as a single email. Emails are sent
 * after commit on another thread, hence the timeouts.
 */
class AssignmentEmailTests extends IntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2030, 5, 10);
    private static final long WAIT_MS = 5000;
    // How long to watch for an email that must never be sent
    private static final long QUIET_MS = 1000;

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
    void severalShiftsForOneWorkerSendOneEmail() throws Exception {
        mockMvc.perform(postJson("/api/worker-shifts", """
                        [
                          { "workerId": %1$d, "zoneId": %2$d, "date": "2030-05-11", "startTime": "09:00", "endTime": "13:00" },
                          { "workerId": %1$d, "zoneId": %2$d, "date": "2030-05-10", "startTime": "09:00", "endTime": "13:00" },
                          { "workerId": %1$d, "zoneId": %2$d, "date": "2030-05-12", "startTime": "18:00", "endTime": "02:00" }
                        ]
                        """.formatted(worker.getUserId(), hall.getId())).with(as(owner)))
                .andExpect(status().isCreated());

        List<EmailRequestDTO> emails = emailsSent(1);
        assertEquals("worker@example.test", emails.get(0).to());
        assertEquals("You have 3 new shifts", emails.get(0).subject());
    }

    @Test
    void shiftsForTwoWorkersSendOneEmailEach() throws Exception {
        mockMvc.perform(postJson("/api/worker-shifts", """
                        [
                          { "workerId": %1$d, "zoneId": %3$d, "date": "2030-05-10", "startTime": "09:00", "endTime": "13:00" },
                          { "workerId": %2$d, "zoneId": %3$d, "date": "2030-05-10", "startTime": "09:00", "endTime": "17:00" },
                          { "workerId": %1$d, "zoneId": %3$d, "date": "2030-05-10", "startTime": "13:00", "endTime": "17:00" }
                        ]
                        """.formatted(worker.getUserId(), colleague.getUserId(), hall.getId())).with(as(owner)))
                .andExpect(status().isCreated());

        List<EmailRequestDTO> emails = emailsSent(2);
        assertEquals("colleague@example.test", emails.get(0).to());
        assertEquals("New shift on 10/05/2030", emails.get(0).subject());
        assertEquals("worker@example.test", emails.get(1).to());
        assertEquals("You have 2 new shifts", emails.get(1).subject());
    }

    @Test
    void aShiftBatchThatFailsHalfwaySendsNoEmail() throws Exception {
        createShift(worker, hall, DAY, "18:00", "23:00");

        mockMvc.perform(postJson("/api/worker-shifts", """
                        [
                          { "workerId": %1$d, "zoneId": %3$d, "date": "2030-05-10", "startTime": "09:00", "endTime": "12:00" },
                          { "workerId": %2$d, "zoneId": %3$d, "date": "2030-05-10", "startTime": "09:00", "endTime": "12:00" },
                          { "workerId": %1$d, "zoneId": %3$d, "date": "2030-05-10", "startTime": "20:00", "endTime": "21:00" }
                        ]
                        """.formatted(worker.getUserId(), colleague.getUserId(), hall.getId())).with(as(owner)))
                .andExpect(status().isConflict());

        verify(emailService, after(QUIET_MS).never()).send(any());
    }

    @Test
    void severalEventsForOneWorkerSendOneEmail() throws Exception {
        createShift(worker, hall, DAY, "18:00", "23:00");
        Event dinner = createEvent(hall, DAY.atTime(19, 0));
        Event cocktails = createEvent(hall, DAY.atTime(22, 30));

        mockMvc.perform(postJson("/api/worker-events", """
                        [ { "workerId": %1$d, "eventId": %3$d }, { "workerId": %1$d, "eventId": %2$d } ]
                        """.formatted(worker.getUserId(), dinner.getId(), cocktails.getId())).with(as(owner)))
                .andExpect(status().isCreated());

        List<EmailRequestDTO> emails = emailsSent(1);
        assertEquals("worker@example.test", emails.get(0).to());
        assertEquals("You have been assigned to 2 events", emails.get(0).subject());
    }

    @Test
    void eventsForTwoWorkersSendOneEmailEach() throws Exception {
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

        List<EmailRequestDTO> emails = emailsSent(2);
        assertEquals("colleague@example.test", emails.get(0).to());
        assertEquals("worker@example.test", emails.get(1).to());
        assertEquals("You have been assigned to 2 events", emails.get(1).subject());
    }

    @Test
    void anEventBatchThatFailsHalfwaySendsNoEmail() throws Exception {
        createShift(worker, hall, DAY, "18:00", "23:00");
        Event dinner = createEvent(hall, DAY.atTime(19, 0));

        mockMvc.perform(postJson("/api/worker-events", """
                        [ { "workerId": %1$d, "eventId": %2$d }, { "workerId": %1$d, "eventId": %2$d } ]
                        """.formatted(worker.getUserId(), dinner.getId())).with(as(owner)))
                .andExpect(status().isConflict());

        verify(emailService, after(QUIET_MS).never()).send(any());
    }

    @Test
    void aShiftAndTheEventsItCoversGoInOneEmail() throws Exception {
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
        List<EmailRequestDTO> emails = emailsSent(1);
        assertEquals("worker@example.test", emails.get(0).to());
        assertEquals("You have 1 new shift and 2 events", emails.get(0).subject());
    }

    @Test
    void shiftsWithEventsForTwoWorkersSendOneEmailEach() throws Exception {
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

        List<EmailRequestDTO> emails = emailsSent(2);
        assertEquals("You have 1 new shift and 1 event", emails.get(0).subject());
        assertEquals("You have 1 new shift and 1 event", emails.get(1).subject());
    }

    @Test
    void anEventTheNewShiftDoesNotCoverSavesNothingAndSendsNoEmail() throws Exception {
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
        verify(emailService, after(QUIET_MS).never()).send(any());
    }

    @Test
    void shiftsWithNoEventsAreJustShifts() throws Exception {
        mockMvc.perform(postJson("/api/worker-shifts/with-events", """
                        { "shifts": [ { "workerId": %d, "zoneId": %d, "date": "2030-05-10", "startTime": "18:00", "endTime": "23:00" } ], "assignments": [] }
                        """.formatted(worker.getUserId(), hall.getId())).with(as(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assignments", hasSize(0)));

        assertEquals("New shift on 10/05/2030", emailsSent(1).get(0).subject());
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

    // Waits for exactly this many emails and returns them sorted by recipient, since they arrive from other threads
    private List<EmailRequestDTO> emailsSent(int count) {
        ArgumentCaptor<EmailRequestDTO> email = ArgumentCaptor.forClass(EmailRequestDTO.class);
        verify(emailService, timeout(WAIT_MS).times(count)).send(email.capture());
        verify(emailService, after(QUIET_MS).times(count)).send(any());
        return email.getAllValues().stream().sorted(Comparator.comparing(EmailRequestDTO::to)).toList();
    }
}
