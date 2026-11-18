package com.moga.khronosdemo.api;

import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.TimeEntryChange;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.entities.WorkerRate;
import com.moga.khronosdemo.entities.Zone;
import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Time entries are the record of the hours worked, so nothing that would delete them can be deleted:
 * not the worker, not their account, not the establishment and not its owner. The API answers 409 and
 * the database refuses as well. Deleting a shift or the author of a change keeps the entries.
 */
class TimeEntryRetentionTests extends IntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2030, 5, 10);

    private User admin;
    private User owner;
    private Establishment venue;
    private Zone bar;
    private Worker worker;
    private Long entryId;

    @BeforeEach
    void setUp() throws Exception {
        admin = createAdmin("admin");
        owner = createOwner("owner");
        venue = createEstablishment(owner);
        bar = createZone(venue, "Bar");
        worker = createWorker(venue, "worker");
        createShift(worker, bar, DAY, "18:00", "02:00");
        workerRateRepository.save(new WorkerRate(worker, new BigDecimal("10.00"), DAY, Instant.now()));

        clock.setNow(Instant.parse("2030-05-10T16:00:00Z"));
        entryId = longAt(mockMvc.perform(post("/api/time-entries/clock-in").with(as(worker)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
        clock.setNow(Instant.parse("2030-05-11T09:00:00Z"));
        mockMvc.perform(putJson("/api/time-entries/{id}", """
                        { "clockIn": "2030-05-10T16:00:00Z", "clockOut": "2030-05-11T00:00:00Z", "breakMinutes": 0,
                          "reason": "Forgot to clock out" }
                        """, entryId).with(as(owner)))
                .andExpect(status().isOk());
    }

    @Test
    void aWorkerWithTimeEntriesCannotBeDeleted() throws Exception {
        mockMvc.perform(deleteJson("/api/workers/{id}", worker.getUserId()).with(as(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot delete a worker who has time entries; deactivate them"
                        + " instead (PUT /api/workers/" + worker.getUserId() + "/active) to keep their time entries"));

        assertTrue(workerRepository.existsById(worker.getUserId()));
        assertEverythingKept();
    }

    @Test
    void theAccountOfAWorkerWithTimeEntriesCannotBeDeleted() throws Exception {
        mockMvc.perform(deleteJson("/api/users/{id}", worker.getUserId()).with(as(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot delete the account of a worker who has time entries;"
                        + " deactivate the worker instead (PUT /api/workers/" + worker.getUserId() + "/active)"
                        + " to keep their time entries"));

        assertTrue(userRepository.existsById(worker.getUserId()));
        assertEverythingKept();
    }

    @Test
    void anEstablishmentWithTimeEntriesCannotBeDeleted() throws Exception {
        mockMvc.perform(deleteJson("/api/establishments/{id}", venue.getId()).with(as(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot delete an establishment that has time entries;"
                        + " deactivate its workers instead to keep the time entries"));

        assertTrue(establishmentRepository.existsById(venue.getId()));
        assertEverythingKept();
    }

    @Test
    void anOwnerWhoseEstablishmentsHaveTimeEntriesCannotBeDeleted() throws Exception {
        mockMvc.perform(deleteJson("/api/users/{id}", owner.getId()).with(as(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot delete an owner whose establishments have time entries;"
                        + " deactivate their workers instead to keep the time entries"));

        assertTrue(userRepository.existsById(owner.getId()));
        assertTrue(establishmentRepository.existsById(venue.getId()));
        assertEverythingKept();
    }

    @Test
    void aVoidedEntryIsPartOfTheRecordToo() throws Exception {
        mockMvc.perform(putJson("/api/time-entries/{id}/void", """
                        { "reason": "Entered by mistake" }
                        """, entryId).with(as(owner)))
                .andExpect(status().isOk());

        mockMvc.perform(deleteJson("/api/workers/{id}", worker.getUserId()).with(as(admin)))
                .andExpect(status().isConflict());
        mockMvc.perform(deleteJson("/api/establishments/{id}", venue.getId()).with(as(admin)))
                .andExpect(status().isConflict());
        assertEquals(1, timeEntryRepository.count());
        assertEquals(3, timeEntryChangeRepository.count());
    }

    @Test
    void withoutTimeEntriesWorkersEstablishmentsAndOwnersCanStillBeDeleted() throws Exception {
        // A worker who never clocked in goes with their account, shifts and rates
        Worker newcomer = createWorker(venue, "newcomer");
        createShift(newcomer, bar, DAY.plusDays(1), "10:00", "14:00");
        workerRateRepository.save(new WorkerRate(newcomer, new BigDecimal("9.00"), DAY, Instant.now()));
        mockMvc.perform(deleteJson("/api/workers/{id}", newcomer.getUserId()).with(as(admin)))
                .andExpect(status().isNoContent());
        assertFalse(userRepository.existsById(newcomer.getUserId()));
        assertEquals(1, workerRateRepository.count());

        User otherOwner = createOwner("other-owner");
        Establishment emptyVenue = createEstablishment(otherOwner);
        createWorker(emptyVenue, "other-worker");
        Establishment secondEmptyVenue = createEstablishment(otherOwner);
        mockMvc.perform(deleteJson("/api/establishments/{id}", emptyVenue.getId()).with(as(admin)))
                .andExpect(status().isNoContent());
        mockMvc.perform(deleteJson("/api/users/{id}", otherOwner.getId()).with(as(admin)))
                .andExpect(status().isNoContent());
        assertFalse(establishmentRepository.existsById(secondEmptyVenue.getId()));

        assertEverythingKept();
    }

    @Test
    void theDatabaseRefusesToDeleteEntriesOrWhatTheyBelongTo() {
        // Only the ON DELETE actions of the time entries' own foreign keys are under test here
        assertEquals("r", onDelete("fk_time_entries_worker"));
        assertEquals("r", onDelete("fk_time_entries_establishment"));
        assertEquals("r", onDelete("fk_time_entry_changes_time_entry"));
        assertEquals("n", onDelete("fk_time_entries_shift"));
        assertEquals("n", onDelete("fk_time_entry_changes_changed_by"));

        // Straight SQL, without the service checks: the worker's shift goes first, so only the entry is in the way
        jdbcTemplate.update("delete from worker_shifts where worker_id = ?", worker.getUserId());
        DataIntegrityViolationException workerDelete = assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("delete from workers where user_id = ?", worker.getUserId()));
        assertTrue(workerDelete.getMostSpecificCause().getMessage().contains("fk_time_entries_worker"));
        DataIntegrityViolationException entryDelete = assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("delete from time_entries where id = ?", entryId));
        assertTrue(entryDelete.getMostSpecificCause().getMessage().contains("fk_time_entry_changes_time_entry"));

        assertEverythingKept();
    }

    @Test
    void deletingTheAuthorOfAChangeKeepsTheChangeAndTheirName() throws Exception {
        // The venue moves to a new owner, then the old owner's account is deleted
        User newOwner = createOwner("new-owner");
        mockMvc.perform(putJson("/api/establishments/{id}", """
                        { "establishmentName": "Venue", "establishmentAddress": "1 New Street", "ownerId": %d }
                        """.formatted(newOwner.getId()), venue.getId()).with(as(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(deleteJson("/api/users/{id}", owner.getId()).with(as(admin)))
                .andExpect(status().isNoContent());

        TimeEntryChange correction = timeEntryChangeRepository.findByTimeEntry_IdOrderByChangedAtAscIdAsc(entryId).get(1);
        assertEquals("First owner Last owner", correction.getChangedByName());
        mockMvc.perform(getJson("/api/time-entries/{id}/history", entryId).with(as(newOwner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].action", contains("CREATED", "CORRECTED")))
                .andExpect(jsonPath("$[1].changedByUserId").value(nullValue()))
                .andExpect(jsonPath("$[1].changedByName").value("First owner Last owner"));
    }

    // The worker's entry, its two history rows and the worker's rate are all still there
    private void assertEverythingKept() {
        assertTrue(timeEntryRepository.existsById(entryId));
        assertEquals(1, timeEntryRepository.count());
        assertEquals(2, timeEntryChangeRepository.findByTimeEntry_IdOrderByChangedAtAscIdAsc(entryId).size());
        assertEquals(1, workerRateRepository.findByWorker_UserIdOrderByEffectiveFromDesc(worker.getUserId()).size());
    }

    private String onDelete(String constraint) {
        return jdbcTemplate.queryForObject("select confdeltype::text from pg_constraint where conname = ?",
                String.class, constraint);
    }
}
