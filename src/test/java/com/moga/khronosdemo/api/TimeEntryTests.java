package com.moga.khronosdemo.api;

import com.moga.khronosdemo.dtos.request.ClockInRequestDTO;
import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.TimeEntry;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.entities.WorkerShift;
import com.moga.khronosdemo.entities.Zone;
import com.moga.khronosdemo.exceptions.BusinessRuleException;
import com.moga.khronosdemo.services.TimeEntryService;
import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Workers clocking themselves in and out. The venue is in Europe/Madrid, two hours ahead of UTC in
 * May, so 18:00 local time is 16:00Z.
 */
class TimeEntryTests extends IntegrationTest {

    private static final LocalDate FRIDAY = LocalDate.of(2030, 5, 10);

    @Autowired
    private TimeEntryService timeEntryService;

    private User owner;
    private Establishment venue;
    private Zone bar;
    private Worker worker;
    private Worker colleague;

    @BeforeEach
    void setUp() {
        owner = createOwner("owner");
        venue = createEstablishment(owner);
        bar = createZone(venue, "Bar");
        worker = createWorker(venue, "worker");
        colleague = createWorker(venue, "colleague");
    }

    @Test
    void aWorkerClocksInAndOutWithTheServersTime() throws Exception {
        WorkerShift night = createShift(worker, bar, FRIDAY, "18:00", "02:00");
        clock.setNow(Instant.parse("2030-05-10T15:58:00Z"));

        Long id = longAt(mockMvc.perform(postJson("/api/time-entries/clock-in", """
                        { "note": "Came in through the back door" }
                        """).with(as(worker)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workerId").value(worker.getUserId()))
                .andExpect(jsonPath("$.workerFirstName").value("First worker"))
                .andExpect(jsonPath("$.workerLastName").value("Last worker"))
                .andExpect(jsonPath("$.establishmentId").value(venue.getId()))
                .andExpect(jsonPath("$.businessDate").value("2030-05-10"))
                .andExpect(jsonPath("$.clockIn").value("2030-05-10T15:58:00Z"))
                .andExpect(jsonPath("$.clockOut").value(nullValue()))
                .andExpect(jsonPath("$.breakMinutes").value(0))
                .andExpect(jsonPath("$.workedMinutes").value(nullValue()))
                .andExpect(jsonPath("$.shiftId").value(night.getId()))
                .andExpect(jsonPath("$.source").value("WORKER"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.isEdited").value(false))
                .andExpect(jsonPath("$.isStale").value(false))
                .andExpect(jsonPath("$.edited").doesNotExist())
                .andExpect(jsonPath("$.stale").doesNotExist())
                .andExpect(jsonPath("$.note").value("Came in through the back door"))
                .andReturn(), "$.id");

        mockMvc.perform(getJson("/api/time-entries/me/open").with(as(worker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        clock.setNow(Instant.parse("2030-05-11T00:10:30Z"));
        mockMvc.perform(postJson("/api/time-entries/clock-out", """
                        { "breakMinutes": 30 }
                        """).with(as(worker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.clockOut").value("2030-05-11T00:10:30Z"))
                .andExpect(jsonPath("$.breakMinutes").value(30))
                // 8 h 12 min 30 s, counted in whole minutes, minus the break
                .andExpect(jsonPath("$.workedMinutes").value(462))
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.note").value("Came in through the back door"));

        mockMvc.perform(getJson("/api/time-entries/me/open").with(as(worker)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mockMvc.perform(getJson("/api/time-entries/{id}/history", id).with(as(worker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].action", contains("CREATED", "CLOCKED_OUT")))
                .andExpect(jsonPath("$[0].changedAt").value("2030-05-10T15:58:00Z"))
                .andExpect(jsonPath("$[0].changedByUserId").value(worker.getUserId()))
                .andExpect(jsonPath("$[0].changedByName").value("First worker Last worker"))
                .andExpect(jsonPath("$[0].reason").value(nullValue()))
                .andExpect(jsonPath("$[0].clockOut").value(nullValue()))
                .andExpect(jsonPath("$[1].clockOut").value("2030-05-11T00:10:30Z"))
                .andExpect(jsonPath("$[1].breakMinutes").value(30));
    }

    @Test
    void aSecondClockInOrAClockOutWithoutAnEntryIsRejected() throws Exception {
        clock.setNow(Instant.parse("2030-05-10T16:00:00Z"));

        mockMvc.perform(post("/api/time-entries/clock-out").with(as(worker)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You have no open time entry"));

        // The body is optional
        mockMvc.perform(post("/api/time-entries/clock-in").with(as(worker)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.note").value(nullValue()));

        clock.advance(Duration.ofMinutes(5));
        mockMvc.perform(post("/api/time-entries/clock-in").with(as(worker)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You already have an open time entry"));

        assertEquals(1, timeEntryRepository.count());
    }

    @Test
    void validatesTheClockOutBody() throws Exception {
        clock.setNow(Instant.parse("2030-05-10T16:00:00Z"));
        mockMvc.perform(post("/api/time-entries/clock-in").with(as(worker))).andExpect(status().isCreated());

        mockMvc.perform(postJson("/api/time-entries/clock-out", """
                        { "breakMinutes": 601 }
                        """).with(as(worker)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.breakMinutes").value("must be less than or equal to 600"));

        clock.advance(Duration.ofMinutes(20));
        mockMvc.perform(postJson("/api/time-entries/clock-out", """
                        { "breakMinutes": 30 }
                        """).with(as(worker)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("The break cannot be longer than the time between clock-in and clock-out"));
    }

    @Test
    void anInactiveWorkerCannotClockIn() {
        worker.setActive(false);
        workerRepository.save(worker);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> timeEntryService.clockIn(worker.getUserId(), new ClockInRequestDTO(null)));
        assertEquals("Worker is not active", ex.getMessage());
        assertEquals(0, timeEntryRepository.count());
    }

    @Test
    void aClockInAfterMidnightCountsForTheNightShiftThatStartedTheDayBefore() throws Exception {
        WorkerShift night = createShift(worker, bar, FRIDAY, "18:00", "02:00");
        // 00:30 on Saturday in Madrid
        clock.setNow(Instant.parse("2030-05-10T22:30:00Z"));

        mockMvc.perform(post("/api/time-entries/clock-in").with(as(worker)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shiftId").value(night.getId()))
                .andExpect(jsonPath("$.businessDate").value("2030-05-10"));
    }

    @Test
    void aClockInUpToThreeHoursBeforeAShiftCountsForIt() throws Exception {
        WorkerShift evening = createShift(worker, bar, FRIDAY, "18:00", "23:00");
        createShift(colleague, bar, FRIDAY, "18:00", "23:00");

        // 15:00 in Madrid, exactly three hours early
        clock.setNow(Instant.parse("2030-05-10T13:00:00Z"));
        mockMvc.perform(post("/api/time-entries/clock-in").with(as(worker)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shiftId").value(evening.getId()));

        // 14:59 is too early to count for the 18:00 shift
        clock.setNow(Instant.parse("2030-05-10T12:59:00Z"));
        mockMvc.perform(post("/api/time-entries/clock-in").with(as(colleague)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shiftId").value(nullValue()))
                .andExpect(jsonPath("$.businessDate").value("2030-05-10"));
    }

    @Test
    void withoutAShiftTheBusinessDateIsTheLocalDateOfTheClockIn() throws Exception {
        // 23:30 UTC on Friday is already 01:30 on Saturday in Madrid
        clock.setNow(Instant.parse("2030-05-10T23:30:00Z"));

        mockMvc.perform(post("/api/time-entries/clock-in").with(as(worker)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shiftId").value(nullValue()))
                .andExpect(jsonPath("$.businessDate").value("2030-05-11"));
    }

    @Test
    void creatingAShiftMatchesTheEntriesAlreadyClockedInWithinItsWindow() throws Exception {
        // 00:30 on Saturday in Madrid, before the Friday night shift was entered: it counts for Saturday
        clock.setNow(Instant.parse("2030-05-10T22:30:00Z"));
        Long id = longAt(mockMvc.perform(post("/api/time-entries/clock-in").with(as(worker)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shiftId").value(nullValue()))
                .andExpect(jsonPath("$.businessDate").value("2030-05-11"))
                .andReturn(), "$.id");
        // Left as they are: an entry before the window (14:00), a voided one inside it and a colleague's
        TimeEntry tooEarly = createTimeEntry(worker, null, FRIDAY, "2030-05-10T12:00:00Z", "2030-05-10T12:30:00Z", 0);
        TimeEntry voided = createTimeEntry(worker, null, FRIDAY, "2030-05-10T16:00:00Z", "2030-05-10T17:00:00Z", 0);
        voided.setVoidedAt(Instant.parse("2030-05-10T17:05:00Z"));
        timeEntryRepository.save(voided);
        TimeEntry colleagues = createTimeEntry(colleague, null, FRIDAY, "2030-05-10T16:00:00Z", "2030-05-10T20:00:00Z", 0);

        Long night = longAt(mockMvc.perform(postJson("/api/worker-shifts", """
                        [ { "workerId": %d, "zoneId": %d, "date": "2030-05-10", "startTime": "18:00", "endTime": "02:00" } ]
                        """.formatted(worker.getUserId(), bar.getId())).with(as(owner)))
                .andExpect(status().isCreated())
                .andReturn(), "$[0].id");

        mockMvc.perform(getJson("/api/time-entries/me/open").with(as(worker)))
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.shiftId").value(night))
                .andExpect(jsonPath("$.businessDate").value("2030-05-10"));
        assertNull(timeEntryRepository.findById(tooEarly.getId()).orElseThrow().getShift());
        assertNull(timeEntryRepository.findById(voided.getId()).orElseThrow().getShift());
        assertNull(timeEntryRepository.findById(colleagues.getId()).orElseThrow().getShift());
        // Matching changes no time, so the history still only has the clock-in
        assertEquals(1, timeEntryChangeRepository.count());
    }

    @Test
    void anEntryInTheWindowOfTwoNewShiftsGoesToTheOneThatStartsFirst() throws Exception {
        // 12:30 in Madrid: during a 10:00-14:00 shift, and within three hours of a 15:00-18:00 one
        TimeEntry entry = createTimeEntry(worker, null, FRIDAY, "2030-05-10T10:30:00Z", "2030-05-10T11:30:00Z", 0);

        Long morning = longAt(mockMvc.perform(postJson("/api/worker-shifts", """
                        [
                          { "workerId": %1$d, "zoneId": %2$d, "date": "2030-05-10", "startTime": "15:00", "endTime": "18:00" },
                          { "workerId": %1$d, "zoneId": %2$d, "date": "2030-05-10", "startTime": "10:00", "endTime": "14:00" }
                        ]
                        """.formatted(worker.getUserId(), bar.getId())).with(as(owner)))
                .andExpect(status().isCreated())
                .andReturn(), "$[1].id");

        assertEquals(morning, timeEntryRepository.findById(entry.getId()).orElseThrow().getShift().getId());
    }

    @Test
    void anEntryOpenForMoreThanSixteenHoursIsStale() throws Exception {
        clock.setNow(Instant.parse("2030-05-10T16:00:00Z"));
        mockMvc.perform(post("/api/time-entries/clock-in").with(as(worker))).andExpect(status().isCreated());

        clock.setNow(Instant.parse("2030-05-11T08:00:00Z"));
        mockMvc.perform(getJson("/api/time-entries/me/open").with(as(worker)))
                .andExpect(jsonPath("$.isStale").value(false));

        clock.setNow(Instant.parse("2030-05-11T08:01:00Z"));
        mockMvc.perform(getJson("/api/time-entries/me/open").with(as(worker)))
                .andExpect(jsonPath("$.isStale").value(true));
        mockMvc.perform(getJson("/api/time-entries/establishment/{id}/open", venue.getId()).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].workerId").value(worker.getUserId()))
                .andExpect(jsonPath("$[0].isStale").value(true));

        // After more than a day the worker cannot close it themselves
        clock.setNow(Instant.parse("2030-05-11T16:00:01Z"));
        mockMvc.perform(post("/api/time-entries/clock-out").with(as(worker)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "This time entry has been open for more than 24 hours; ask a manager to correct it"));
    }

    @Test
    void deletingTheMatchedShiftKeepsTheEntry() throws Exception {
        WorkerShift night = createShift(worker, bar, FRIDAY, "18:00", "02:00");
        clock.setNow(Instant.parse("2030-05-10T22:30:00Z"));
        Long id = longAt(mockMvc.perform(post("/api/time-entries/clock-in").with(as(worker)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");

        mockMvc.perform(deleteJson("/api/worker-shifts/{id}", night.getId()).with(as(owner)))
                .andExpect(status().isNoContent());

        TimeEntry entry = timeEntryRepository.findById(id).orElseThrow();
        assertNull(entry.getShift());
        assertEquals(FRIDAY, entry.getBusinessDate());
        assertEquals(1, timeEntryChangeRepository.count());
    }

    @Test
    void movingTheMatchedShiftAwayUnlinksTheEntryAndAMoveWithinTheWindowKeepsIt() throws Exception {
        WorkerShift evening = createShift(worker, bar, FRIDAY, "18:00", "23:00");
        TimeEntry entry = createTimeEntry(worker, evening, FRIDAY, "2030-05-10T16:05:00Z", "2030-05-10T21:00:00Z", 0);

        // Starting at 20:00 the window opens at 17:00, so the 18:05 clock-in still counts
        mockMvc.perform(putJson("/api/worker-shifts/{id}", """
                        { "workerId": %d, "zoneId": %d, "date": "2030-05-10", "startTime": "20:00", "endTime": "23:00" }
                        """.formatted(worker.getUserId(), bar.getId()), evening.getId()).with(as(owner)))
                .andExpect(status().isOk());
        assertEquals(evening.getId(), timeEntryRepository.findById(entry.getId()).orElseThrow().getShift().getId());

        mockMvc.perform(putJson("/api/worker-shifts/{id}", """
                        { "workerId": %d, "zoneId": %d, "date": "2030-05-10", "startTime": "18:00", "endTime": "23:00" }
                        """.formatted(colleague.getUserId(), bar.getId()), evening.getId()).with(as(owner)))
                .andExpect(status().isOk());
        TimeEntry unlinked = timeEntryRepository.findById(entry.getId()).orElseThrow();
        assertNull(unlinked.getShift());
        assertEquals(FRIDAY, unlinked.getBusinessDate());
    }

    @Test
    void aWorkerWithTimeEntriesCannotMoveToAnotherEstablishment() throws Exception {
        Establishment secondVenue = createEstablishment(owner);
        createTimeEntry(worker, null, FRIDAY, "2030-05-10T16:00:00Z", "2030-05-10T20:00:00Z", 0);

        mockMvc.perform(putJson("/api/workers/{id}", """
                        { "userId": %1$d, "establishmentId": %2$d, "isActive": true }
                        """.formatted(worker.getUserId(), secondVenue.getId()), worker.getUserId()).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot move a worker with time entries to another establishment"));
    }

    @Test
    void onlyWorkersClockInAndOut() throws Exception {
        mockMvc.perform(post("/api/time-entries/clock-in").with(as(owner))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/time-entries/clock-out").with(as(owner))).andExpect(status().isForbidden());
        mockMvc.perform(getJson("/api/time-entries/me/open").with(as(owner))).andExpect(status().isForbidden());
    }
}
