package com.moga.khronosdemo.api;

import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.Event;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.entities.WorkerShift;
import com.moga.khronosdemo.entities.Zone;
import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A shift whose end time is not after its start time ends on the next day. Overlap and coverage are
 * checked on the real interval, so a Friday night shift meets Saturday's shifts and events.
 */
class NightShiftTests extends IntegrationTest {

    private static final LocalDate FRIDAY = LocalDate.of(2030, 5, 10);
    private static final LocalDate SATURDAY = FRIDAY.plusDays(1);

    private User owner;
    private Zone bar;
    private Zone terrace;
    private Worker worker;

    @BeforeEach
    void setUp() {
        owner = createOwner("owner");
        Establishment venue = createEstablishment(owner);
        bar = createZone(venue, "Bar");
        terrace = createZone(venue, "Terrace");
        worker = createWorker(venue, "worker");
    }

    @Test
    void createsAShiftThatEndsAfterMidnight() throws Exception {
        mockMvc.perform(postJson("/api/worker-shifts", """
                        [
                          { "workerId": %1$d, "zoneId": %2$d, "date": "2030-05-10", "startTime": "18:00", "endTime": "02:00" },
                          { "workerId": %1$d, "zoneId": %2$d, "date": "2030-05-11", "startTime": "10:00", "endTime": "14:00" }
                        ]
                        """.formatted(worker.getUserId(), bar.getId())).with(as(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].date").value("2030-05-10"))
                .andExpect(jsonPath("$[0].endTime").value("02:00:00"))
                .andExpect(jsonPath("$[0].endsNextDay").value(true))
                .andExpect(jsonPath("$[0].durationMinutes").value(480))
                .andExpect(jsonPath("$[1].endsNextDay").value(false))
                .andExpect(jsonPath("$[1].durationMinutes").value(240));

        // The listing still filters on the day the shift starts
        mockMvc.perform(getJson("/api/worker-shifts/worker/{id}?from=2030-05-10&to=2030-05-10", worker.getUserId())
                        .with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].endsNextDay").value(true));
    }

    @Test
    void rejectsAShiftWithNoLengthOrLongerThanSixteenHours() throws Exception {
        mockMvc.perform(postJson("/api/worker-shifts", """
                        [ { "workerId": %d, "zoneId": %d, "date": "2030-05-10", "startTime": "18:00", "endTime": "18:00" } ]
                        """.formatted(worker.getUserId(), bar.getId())).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Shift [0]: A shift cannot start and end at the same time"));

        mockMvc.perform(postJson("/api/worker-shifts", """
                        [ { "workerId": %d, "zoneId": %d, "date": "2030-05-10", "startTime": "08:00", "endTime": "00:01" } ]
                        """.formatted(worker.getUserId(), bar.getId())).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Shift [0]: A shift cannot last more than 16 hours"));

        mockMvc.perform(postJson("/api/worker-shifts", """
                        [ { "workerId": %d, "zoneId": %d, "date": "2030-05-10", "startTime": "20:00", "endTime": "12:30" } ]
                        """.formatted(worker.getUserId(), bar.getId())).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Shift [0]: A shift cannot last more than 16 hours"));

        assertEquals(0, workerShiftRepository.count());

        // Exactly 16 hours is allowed
        mockMvc.perform(postJson("/api/worker-shifts", """
                        [ { "workerId": %d, "zoneId": %d, "date": "2030-05-10", "startTime": "08:00", "endTime": "00:00" } ]
                        """.formatted(worker.getUserId(), bar.getId())).with(as(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].durationMinutes").value(960))
                .andExpect(jsonPath("$[0].endsNextDay").value(true));
    }

    @Test
    void aNightShiftOverlapsTheNextDaysEarlyShift() throws Exception {
        createShift(worker, bar, FRIDAY, "18:00", "02:00");

        mockMvc.perform(postJson("/api/worker-shifts", """
                        [ { "workerId": %d, "zoneId": %d, "date": "2030-05-11", "startTime": "01:00", "endTime": "05:00" } ]
                        """.formatted(worker.getUserId(), terrace.getId())).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Shift [0]: The worker already has a shift that overlaps with 01:00-05:00 on 2030-05-11"));

        // Starting exactly when the night shift ends is fine
        mockMvc.perform(postJson("/api/worker-shifts", """
                        [ { "workerId": %d, "zoneId": %d, "date": "2030-05-11", "startTime": "02:00", "endTime": "06:00" } ]
                        """.formatted(worker.getUserId(), terrace.getId())).with(as(owner)))
                .andExpect(status().isCreated());
    }

    @Test
    void anEarlyShiftBlocksANightShiftOnTheDayBefore() throws Exception {
        createShift(worker, bar, SATURDAY, "01:00", "05:00");

        mockMvc.perform(postJson("/api/worker-shifts", """
                        [ { "workerId": %d, "zoneId": %d, "date": "2030-05-10", "startTime": "18:00", "endTime": "02:00" } ]
                        """.formatted(worker.getUserId(), bar.getId())).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Shift [0]: The worker already has a shift that overlaps with 18:00-02:00 (+1) on 2030-05-10"));

        // A night shift on Thursday that ends before Friday's evening does not touch either
        mockMvc.perform(postJson("/api/worker-shifts", """
                        [ { "workerId": %d, "zoneId": %d, "date": "2030-05-09", "startTime": "22:00", "endTime": "04:00" } ]
                        """.formatted(worker.getUserId(), bar.getId())).with(as(owner)))
                .andExpect(status().isCreated());
    }

    @Test
    void checksABatchAgainstItselfAcrossMidnight() throws Exception {
        mockMvc.perform(postJson("/api/worker-shifts", """
                        [
                          { "workerId": %1$d, "zoneId": %2$d, "date": "2030-05-10", "startTime": "20:00", "endTime": "03:00" },
                          { "workerId": %1$d, "zoneId": %2$d, "date": "2030-05-11", "startTime": "02:00", "endTime": "04:00" }
                        ]
                        """.formatted(worker.getUserId(), bar.getId())).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(startsWith("Shift [1]: The worker already has a shift that overlaps")));

        assertEquals(0, workerShiftRepository.count());
    }

    @Test
    void anUpdateThatRunsIntoTheNextDaysShiftIsRejected() throws Exception {
        createShift(worker, bar, SATURDAY, "09:00", "13:00");
        Long friday = createShift(worker, bar, FRIDAY, "18:00", "23:00").getId();

        mockMvc.perform(putJson("/api/worker-shifts/{id}", """
                        { "workerId": %d, "zoneId": %d, "date": "2030-05-10", "startTime": "20:00", "endTime": "10:00" }
                        """.formatted(worker.getUserId(), bar.getId()), friday).with(as(owner)))
                .andExpect(status().isConflict());

        mockMvc.perform(putJson("/api/worker-shifts/{id}", """
                        { "workerId": %d, "zoneId": %d, "date": "2030-05-10", "startTime": "20:00", "endTime": "04:00" }
                        """.formatted(worker.getUserId(), bar.getId()), friday).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endsNextDay").value(true))
                .andExpect(jsonPath("$.durationMinutes").value(480));
    }

    @Test
    void theNightShiftOfTheDayBeforeCoversAnEventInTheEarlyHours() throws Exception {
        createShift(worker, bar, FRIDAY, "18:00", "02:00");
        Event lateSet = createEvent(bar, SATURDAY.atTime(1, 0));
        Event closing = createEvent(bar, SATURDAY.atTime(2, 0));

        mockMvc.perform(postJson("/api/worker-events", """
                        [ { "workerId": %d, "eventId": %d } ]
                        """.formatted(worker.getUserId(), lateSet.getId())).with(as(owner)))
                .andExpect(status().isCreated());

        // The shift ends at 02:00, so it is no longer running at 02:00
        mockMvc.perform(postJson("/api/worker-events", """
                        [ { "workerId": %d, "eventId": %d } ]
                        """.formatted(worker.getUserId(), closing.getId())).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(startsWith("Assignment [0]: The worker has no shift in zone 'Bar'")));
    }

    @Test
    void shorteningANightShiftUnassignsOnlyTheEventsItNoLongerCovers() throws Exception {
        WorkerShift night = createShift(worker, bar, FRIDAY, "18:00", "02:00");
        Long early = assign(worker, createEvent(bar, FRIDAY.atTime(20, 0))).getId();
        Long late = assign(worker, createEvent(bar, SATURDAY.atTime(1, 0))).getId();

        mockMvc.perform(putJson("/api/worker-shifts/{id}", """
                        { "workerId": %d, "zoneId": %d, "date": "2030-05-10", "startTime": "18:00", "endTime": "23:30" }
                        """.formatted(worker.getUserId(), bar.getId()), night.getId()).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endsNextDay").value(false));

        assertTrue(workerEventRepository.existsById(early));
        assertFalse(workerEventRepository.existsById(late));
    }

    @Test
    void movingAnEventIntoTheEarlyHoursKeepsTheAssignmentWhileTheNightShiftCoversIt() throws Exception {
        createShift(worker, bar, FRIDAY, "18:00", "02:00");
        Event dinner = createEvent(bar, FRIDAY.atTime(21, 0));
        Long assignment = assign(worker, dinner).getId();

        mockMvc.perform(putJson("/api/events/{id}", """
                        { "title": "Late set", "dateTime": "2030-05-11T01:30:00", "zoneId": %d, "peopleNumber": 40 }
                        """.formatted(bar.getId()), dinner.getId()).with(as(owner)))
                .andExpect(status().isOk());
        assertTrue(workerEventRepository.existsById(assignment));

        mockMvc.perform(putJson("/api/events/{id}", """
                        { "title": "Late set", "dateTime": "2030-05-11T02:30:00", "zoneId": %d, "peopleNumber": 40 }
                        """.formatted(bar.getId()), dinner.getId()).with(as(owner)))
                .andExpect(status().isOk());
        assertFalse(workerEventRepository.existsById(assignment));
    }

    @Test
    void dayShiftsKeepTheirOldRules() throws Exception {
        createShift(worker, bar, FRIDAY, "09:00", "13:00");

        mockMvc.perform(postJson("/api/worker-shifts", """
                        [
                          { "workerId": %1$d, "zoneId": %2$d, "date": "2030-05-10", "startTime": "13:00", "endTime": "17:00" },
                          { "workerId": %1$d, "zoneId": %2$d, "date": "2030-05-11", "startTime": "09:00", "endTime": "13:00" }
                        ]
                        """.formatted(worker.getUserId(), bar.getId())).with(as(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[*].endsNextDay", contains(false, false)));

        mockMvc.perform(postJson("/api/worker-shifts", """
                        [ { "workerId": %d, "zoneId": %d, "date": "2030-05-10", "startTime": "12:00", "endTime": "14:00" } ]
                        """.formatted(worker.getUserId(), bar.getId())).with(as(owner)))
                .andExpect(status().isConflict());

        Event lunch = createEvent(bar, FRIDAY.atTime(12, 59));
        mockMvc.perform(postJson("/api/worker-events", """
                        [ { "workerId": %d, "eventId": %d } ]
                        """.formatted(worker.getUserId(), lunch.getId())).with(as(owner)))
                .andExpect(status().isCreated());
    }
}
