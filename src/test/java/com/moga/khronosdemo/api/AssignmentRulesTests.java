package com.moga.khronosdemo.api;

import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.Event;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.entities.WorkerShift;
import com.moga.khronosdemo.entities.Zone;
import com.moga.khronosdemo.support.IntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssignmentRulesTests extends IntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2030, 5, 10);

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private User owner;
    private Zone hall;
    private Zone terrace;
    private Worker worker;
    private WorkerShift eveningShift;

    @BeforeEach
    void setUp() {
        owner = createOwner("owner");
        Establishment venue = createEstablishment(owner);
        hall = createZone(venue, "Hall");
        terrace = createZone(venue, "Terrace");
        worker = createWorker(venue, "worker");
        eveningShift = createShift(worker, hall, DAY, "18:00", "23:00");
    }

    @Test
    void assignsAWorkerWhoseShiftCoversTheEvent() throws Exception {
        Event dinner = createEvent(hall, DAY.atTime(19, 0));
        Event cocktails = createEvent(hall, DAY.atTime(22, 30));

        mockMvc.perform(postJson("/api/worker-events", """
                        [ { "workerId": %1$d, "eventId": %2$d }, { "workerId": %1$d, "eventId": %3$d } ]
                        """.formatted(worker.getUserId(), dinner.getId(), cocktails.getId())).with(as(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].workerId").value(worker.getUserId()))
                .andExpect(jsonPath("$[0].eventId").value(dinner.getId()));
    }

    @Test
    void rejectsAnEventOutsideTheWorkersShift() throws Exception {
        Event lateEvent = createEvent(hall, DAY.atTime(23, 0));
        Event otherZone = createEvent(terrace, DAY.atTime(19, 0));

        mockMvc.perform(postJson("/api/worker-events", """
                        [ { "workerId": %d, "eventId": %d } ]
                        """.formatted(worker.getUserId(), lateEvent.getId())).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(startsWith("Assignment [0]: The worker has no shift in zone 'Hall'")));

        mockMvc.perform(postJson("/api/worker-events", """
                        [ { "workerId": %d, "eventId": %d } ]
                        """.formatted(worker.getUserId(), otherZone.getId())).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(startsWith("Assignment [0]: The worker has no shift in zone 'Terrace'")));

        assertEquals(0, workerEventRepository.count());
    }

    @Test
    void savesNoAssignmentWhenOneOfTheBatchIsInvalid() throws Exception {
        Event dinner = createEvent(hall, DAY.atTime(19, 0));

        mockMvc.perform(postJson("/api/worker-events", """
                        [ { "workerId": %1$d, "eventId": %2$d }, { "workerId": %1$d, "eventId": %2$d } ]
                        """.formatted(worker.getUserId(), dinner.getId())).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Assignment [1]: Worker is already assigned to this event"));

        assertEquals(0, workerEventRepository.count());
    }

    @Test
    void rejectsAnInactiveWorkerEvenWithACoveringShift() throws Exception {
        Event dinner = createEvent(hall, DAY.atTime(19, 0));
        worker.setActive(false);
        workerRepository.save(worker);

        mockMvc.perform(postJson("/api/worker-events", """
                        [ { "workerId": %d, "eventId": %d } ]
                        """.formatted(worker.getUserId(), dinner.getId())).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Assignment [0]: Worker is not active"));

        assertEquals(0, workerEventRepository.count());
    }

    @Test
    void deletingTheCoveringShiftUnassignsTheWorker() throws Exception {
        Event dinner = createEvent(hall, DAY.atTime(19, 0));
        Long assignmentId = assign(worker, dinner).getId();

        mockMvc.perform(deleteJson("/api/worker-shifts/{id}", eveningShift.getId()).with(as(owner)))
                .andExpect(status().isNoContent());

        assertEquals(false, workerEventRepository.existsById(assignmentId));
    }

    @Test
    void movingTheEventOutsideTheShiftUnassignsTheWorker() throws Exception {
        Event dinner = createEvent(hall, DAY.atTime(19, 0));
        Long assignmentId = assign(worker, dinner).getId();

        mockMvc.perform(putJson("/api/events/{id}", """
                        { "title": "Dinner", "dateTime": "2030-05-10T19:00:00", "zoneId": %d, "peopleNumber": 40 }
                        """.formatted(terrace.getId()), dinner.getId()).with(as(owner)))
                .andExpect(status().isOk());

        assertEquals(false, workerEventRepository.existsById(assignmentId));
    }

    @Test
    void aShiftChangeOnlyChecksTheEventsItsIntervalHeld() throws Exception {
        // An assignment of the same worker, saved without the checks, that no shift covers. It lies outside
        // the edited shift's interval, before and after the edit, so the edit doesn't look at it
        Event unrelated = createEvent(terrace, DAY.plusDays(5).atTime(12, 0));
        Long unrelatedId = assign(worker, unrelated).getId();
        Event dinner = createEvent(hall, DAY.atTime(19, 0));
        Long dinnerId = assign(worker, dinner).getId();

        mockMvc.perform(putJson("/api/worker-shifts/{id}", """
                        { "workerId": %d, "zoneId": %d, "date": "2030-05-10", "startTime": "20:00", "endTime": "23:00" }
                        """.formatted(worker.getUserId(), hall.getId()), eveningShift.getId()).with(as(owner)))
                .andExpect(status().isOk());

        assertEquals(false, workerEventRepository.existsById(dinnerId));
        assertEquals(true, workerEventRepository.existsById(unrelatedId));
    }

    @Test
    void aShiftChangeRunsTheSameQueriesHoweverManyAssignmentsTheWorkerHas() throws Exception {
        long[] withoutHistory = statementsToEditAndDeleteShifts(DAY.plusDays(20));

        for (int i = 1; i <= 5; i++) {
            LocalDate day = DAY.minusDays(i);
            createShift(worker, hall, day, "18:00", "23:00");
            assign(worker, createEvent(hall, day.atTime(19, 0)));
            assign(worker, createEvent(hall, day.atTime(21, 0)));
        }
        long[] withHistory = statementsToEditAndDeleteShifts(DAY.plusDays(30));

        assertTrue(withoutHistory[0] > 0 && withoutHistory[1] > 0, "statistics must be enabled in the test profile");
        assertEquals(withoutHistory[0], withHistory[0], "editing a shift");
        assertEquals(withoutHistory[1], withHistory[1], "deleting a shift");
        // Each change unassigned only the event its shift no longer covers; the history is untouched
        assertEquals(10, workerEventRepository.count());
    }

    // Edits one shift and deletes another, each of which covers one assigned event that the change leaves
    // uncovered, and returns the statements each request ran
    private long[] statementsToEditAndDeleteShifts(LocalDate day) throws Exception {
        WorkerShift edited = createShift(worker, hall, day, "18:00", "23:00");
        assign(worker, createEvent(hall, day.atTime(19, 0)));
        WorkerShift deleted = createShift(worker, hall, day.plusDays(1), "18:00", "23:00");
        assign(worker, createEvent(hall, day.plusDays(1).atTime(19, 0)));
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        statistics.clear();
        mockMvc.perform(putJson("/api/worker-shifts/{id}", """
                        { "workerId": %d, "zoneId": %d, "date": "%s", "startTime": "20:00", "endTime": "23:00" }
                        """.formatted(worker.getUserId(), hall.getId(), day), edited.getId()).with(as(owner)))
                .andExpect(status().isOk());
        long edit = statistics.getPrepareStatementCount();

        statistics.clear();
        mockMvc.perform(deleteJson("/api/worker-shifts/{id}", deleted.getId()).with(as(owner)))
                .andExpect(status().isNoContent());
        long delete = statistics.getPrepareStatementCount();
        return new long[]{edit, delete};
    }
}
