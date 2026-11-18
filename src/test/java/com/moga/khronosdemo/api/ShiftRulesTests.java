package com.moga.khronosdemo.api;

import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.entities.Zone;
import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ShiftRulesTests extends IntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2030, 5, 10);

    private User owner;
    private Zone zone;
    private Worker worker;
    private Worker colleague;

    @BeforeEach
    void setUp() {
        owner = createOwner("owner");
        Establishment venue = createEstablishment(owner);
        zone = createZone(venue, "Hall");
        worker = createWorker(venue, "worker");
        colleague = createWorker(venue, "colleague");
    }

    @Test
    void createsABatchOfShifts() throws Exception {
        mockMvc.perform(postJson("/api/worker-shifts", """
                        [
                          { "workerId": %1$d, "zoneId": %3$d, "date": "2030-05-10", "startTime": "09:00", "endTime": "13:00" },
                          { "workerId": %1$d, "zoneId": %3$d, "date": "2030-05-10", "startTime": "13:00", "endTime": "17:00" },
                          { "workerId": %2$d, "zoneId": %3$d, "date": "2030-05-10", "startTime": "09:00", "endTime": "17:00" }
                        ]
                        """.formatted(worker.getUserId(), colleague.getUserId(), zone.getId())).with(as(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[1].startTime").value("13:00:00"));

        assertEquals(3, workerShiftRepository.count());
    }

    @Test
    void rejectsAShiftThatOverlapsAnExistingOne() throws Exception {
        createShift(worker, zone, DAY, "18:00", "23:00");

        mockMvc.perform(postJson("/api/worker-shifts", """
                        [ { "workerId": %d, "zoneId": %d, "date": "2030-05-10", "startTime": "22:00", "endTime": "23:30" } ]
                        """.formatted(worker.getUserId(), zone.getId())).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(startsWith("Shift [0]: The worker already has a shift that overlaps")));

        assertEquals(1, workerShiftRepository.count());
    }

    @Test
    void savesNothingWhenOneShiftOfTheBatchIsInvalid() throws Exception {
        createShift(worker, zone, DAY, "18:00", "23:00");

        mockMvc.perform(postJson("/api/worker-shifts", """
                        [
                          { "workerId": %1$d, "zoneId": %3$d, "date": "2030-05-10", "startTime": "09:00", "endTime": "12:00" },
                          { "workerId": %2$d, "zoneId": %3$d, "date": "2030-05-10", "startTime": "09:00", "endTime": "12:00" },
                          { "workerId": %1$d, "zoneId": %3$d, "date": "2030-05-10", "startTime": "20:00", "endTime": "21:00" }
                        ]
                        """.formatted(worker.getUserId(), colleague.getUserId(), zone.getId())).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(startsWith("Shift [2]: ")));

        assertEquals(1, workerShiftRepository.count());
    }

    @Test
    void rejectsShiftsInTheSameBatchThatOverlapEachOther() throws Exception {
        mockMvc.perform(postJson("/api/worker-shifts", """
                        [
                          { "workerId": %1$d, "zoneId": %2$d, "date": "2030-05-10", "startTime": "09:00", "endTime": "13:00" },
                          { "workerId": %1$d, "zoneId": %2$d, "date": "2030-05-10", "startTime": "12:00", "endTime": "15:00" }
                        ]
                        """.formatted(worker.getUserId(), zone.getId())).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(startsWith("Shift [1]: The worker already has a shift that overlaps")));

        assertEquals(0, workerShiftRepository.count());
    }

    @Test
    void rejectsAnUpdateThatWouldOverlapAnotherShift() throws Exception {
        createShift(worker, zone, DAY, "18:00", "23:00");
        Long morning = createShift(worker, zone, DAY, "09:00", "13:00").getId();

        mockMvc.perform(putJson("/api/worker-shifts/{id}", """
                        { "workerId": %d, "zoneId": %d, "date": "2030-05-10", "startTime": "12:00", "endTime": "19:00" }
                        """.formatted(worker.getUserId(), zone.getId()), morning).with(as(owner)))
                .andExpect(status().isConflict());

        // Moving it without overlapping, including onto its own old slot, is fine
        mockMvc.perform(putJson("/api/worker-shifts/{id}", """
                        { "workerId": %d, "zoneId": %d, "date": "2030-05-10", "startTime": "10:00", "endTime": "14:00" }
                        """.formatted(worker.getUserId(), zone.getId()), morning).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startTime").value("10:00:00"));
    }

    // An end time before the start time used to be rejected; it now means the shift ends the next day
    // (see NightShiftTests). Only a shift with no length at all is still refused.
    @Test
    void rejectsAShiftThatStartsAndEndsAtTheSameTime() throws Exception {
        mockMvc.perform(postJson("/api/worker-shifts", """
                        [ { "workerId": %d, "zoneId": %d, "date": "2030-05-10", "startTime": "22:00", "endTime": "22:00" } ]
                        """.formatted(worker.getUserId(), zone.getId())).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Shift [0]: A shift cannot start and end at the same time"));
    }

    @Test
    void rejectsAShiftForAnInactiveWorker() throws Exception {
        Worker inactive = workerRepository.findById(worker.getUserId()).orElseThrow();
        inactive.setActive(false);
        workerRepository.save(inactive);

        mockMvc.perform(postJson("/api/worker-shifts", """
                        [ { "workerId": %d, "zoneId": %d, "date": "2030-05-10", "startTime": "09:00", "endTime": "12:00" } ]
                        """.formatted(worker.getUserId(), zone.getId())).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Shift [0]: Worker is not active"));
    }
}
