package com.moga.khronosdemo.api;

import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.TimeEntry;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.entities.WorkerRate;
import com.moga.khronosdemo.entities.WorkerShift;
import com.moga.khronosdemo.entities.Zone;
import com.moga.khronosdemo.support.IntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Hours, lateness and costs over a controlled set of shifts and entries. The venue is in Madrid (UTC+2
 * in May) and "now" is Sunday 12 May 2030 at 12:00 local time.
 *
 * <pre>
 * Ana  (10 €/h, 12 €/h from Sat 11)
 *   Fri 10  shift 18:00-02:00 (480)  entry 18:15-02:15, 30 min break (450), 15 min late    LATE
 *   Sat 11  shift 10:00-14:00 (240)  only a voided entry                                    MISSED
 *   Sun 12  no shift                 entry 08:00-10:00 (120)                               UNPLANNED
 *   Mon 13  shift 20:00-23:00 (180)  not over yet                                           UPCOMING
 * Ben  (no rate)
 *   Fri 10  shift 18:00-23:00 (300)  entry 18:05-23:00 (295), 5 min late is on time         OK
 *   Sun 12  no shift                 entry open since 10:00                                 OPEN
 * Dani (inactive, 9.50 €/h)  Fri 10 shift 09:00-13:00 (240), no entry
 * Eve  (active, nothing in the range)
 * Carla (inactive, nothing in the range): not listed
 * </pre>
 */
class ReportTests extends IntegrationTest {

    private static final LocalDate FRI = LocalDate.of(2030, 5, 10);
    private static final LocalDate SAT = FRI.plusDays(1);
    private static final LocalDate SUN = FRI.plusDays(2);
    private static final LocalDate MON = FRI.plusDays(3);
    private static final String RANGE = "?from=2030-05-10&to=2030-05-13";

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private User owner;
    private Establishment venue;
    private Zone bar;
    private Worker ana;
    private Worker ben;
    private WorkerShift anasFriday;

    @BeforeEach
    void setUp() {
        owner = createOwner("owner");
        venue = createEstablishment(owner);
        bar = createZone(venue, "Bar");
        ana = createWorker(venue, "ana");
        ben = createWorker(venue, "ben");
        Worker dani = inactive(createWorker(venue, "dani"));
        createWorker(venue, "eve");
        inactive(createWorker(venue, "carla"));

        rate(ana, "10.00", LocalDate.of(2030, 1, 1));
        rate(ana, "12.00", SAT);
        rate(dani, "9.50", LocalDate.of(2030, 1, 1));

        anasFriday = createShift(ana, bar, FRI, "18:00", "02:00");
        createTimeEntry(ana, anasFriday, FRI, "2030-05-10T16:15:00Z", "2030-05-11T00:15:00Z", 30);
        WorkerShift anasSaturday = createShift(ana, bar, SAT, "10:00", "14:00");
        TimeEntry voided = createTimeEntry(ana, anasSaturday, SAT, "2030-05-11T08:00:00Z", "2030-05-11T12:00:00Z", 0);
        voided.setVoidedAt(Instant.parse("2030-05-11T13:00:00Z"));
        timeEntryRepository.save(voided);
        createTimeEntry(ana, null, SUN, "2030-05-12T06:00:00Z", "2030-05-12T08:00:00Z", 0);
        createShift(ana, bar, MON, "20:00", "23:00");
        createShift(ana, bar, MON.plusDays(1), "20:00", "23:00");

        WorkerShift bensFriday = createShift(ben, bar, FRI, "18:00", "23:00");
        createTimeEntry(ben, bensFriday, FRI, "2030-05-10T16:05:00Z", "2030-05-10T21:00:00Z", 0);
        createTimeEntry(ben, null, SUN, "2030-05-12T08:00:00Z", null, 0);
        createTimeEntry(ben, null, FRI.minusDays(1), "2030-05-09T08:00:00Z", "2030-05-09T12:00:00Z", 0);

        createShift(dani, bar, FRI, "09:00", "13:00");

        // Another owner's venue must not leak into the report
        Establishment otherVenue = createEstablishment(createOwner("other-owner"));
        Worker stranger = createWorker(otherVenue, "stranger");
        createShift(stranger, createZone(otherVenue, "Bar"), FRI, "18:00", "23:00");

        clock.setNow(Instant.parse("2030-05-12T10:00:00Z"));
    }

    @Test
    void addsUpHoursLatenessAndCostsPerWorker() throws Exception {
        mockMvc.perform(getJson("/api/reports/establishment/{id}/hours" + RANGE, venue.getId()).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2030-05-10"))
                .andExpect(jsonPath("$.to").value("2030-05-13"))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.workers[*].firstName", contains("First ana", "First ben", "First dani", "First eve")))
                .andExpect(jsonPath("$.workers[*].isActive", contains(true, true, false, true)))
                .andExpect(jsonPath("$.workers[*].active").doesNotExist())

                .andExpect(jsonPath("$.workers[0].workerId").value(ana.getUserId()))
                .andExpect(jsonPath("$.workers[0].lastName").value("Last ana"))
                .andExpect(jsonPath("$.workers[0].plannedMinutes").value(900))
                .andExpect(jsonPath("$.workers[0].workedMinutes").value(570))
                .andExpect(jsonPath("$.workers[0].differenceMinutes").value(-330))
                .andExpect(jsonPath("$.workers[0].shiftsCount").value(3))
                .andExpect(jsonPath("$.workers[0].entriesCount").value(2))
                .andExpect(jsonPath("$.workers[0].lateCount").value(1))
                .andExpect(jsonPath("$.workers[0].missedShiftsCount").value(1))
                .andExpect(jsonPath("$.workers[0].unplannedCount").value(1))
                .andExpect(jsonPath("$.workers[0].openEntriesCount").value(0))
                .andExpect(jsonPath("$.workers[0].currentHourlyRate").value(12.0))
                // (480 x 10 + 240 x 12 + 180 x 12) / 60 and (450 x 10 + 120 x 12) / 60
                .andExpect(jsonPath("$.workers[0].plannedCost").value(164.0))
                .andExpect(jsonPath("$.workers[0].actualCost").value(99.0))

                .andExpect(jsonPath("$.workers[1].plannedMinutes").value(300))
                .andExpect(jsonPath("$.workers[1].workedMinutes").value(295))
                .andExpect(jsonPath("$.workers[1].differenceMinutes").value(-5))
                .andExpect(jsonPath("$.workers[1].entriesCount").value(2))
                .andExpect(jsonPath("$.workers[1].lateCount").value(0))
                .andExpect(jsonPath("$.workers[1].missedShiftsCount").value(0))
                .andExpect(jsonPath("$.workers[1].unplannedCount").value(1))
                .andExpect(jsonPath("$.workers[1].openEntriesCount").value(1))
                .andExpect(jsonPath("$.workers[1].currentHourlyRate").value(nullValue()))
                .andExpect(jsonPath("$.workers[1].plannedCost").value(nullValue()))
                .andExpect(jsonPath("$.workers[1].actualCost").value(nullValue()))

                .andExpect(jsonPath("$.workers[2].plannedMinutes").value(240))
                .andExpect(jsonPath("$.workers[2].missedShiftsCount").value(1))
                .andExpect(jsonPath("$.workers[2].plannedCost").value(38.0))
                .andExpect(jsonPath("$.workers[2].actualCost").value(0.0))

                .andExpect(jsonPath("$.workers[3].plannedMinutes").value(0))
                .andExpect(jsonPath("$.workers[3].shiftsCount").value(0))
                .andExpect(jsonPath("$.workers[3].plannedCost").value(nullValue()))

                .andExpect(jsonPath("$.totals.plannedMinutes").value(1440))
                .andExpect(jsonPath("$.totals.workedMinutes").value(865))
                .andExpect(jsonPath("$.totals.differenceMinutes").value(-575))
                .andExpect(jsonPath("$.totals.plannedCost").value(202.0))
                // Ben worked 295 minutes without a rate: the known 99.00 leaves him out
                .andExpect(jsonPath("$.totals.actualCost").value(99.0))
                .andExpect(jsonPath("$.totals.costPartial").value(true))
                .andExpect(jsonPath("$.totals.workersWithoutCost").value(1));
    }

    @Test
    void listsEachDayOfAWorkerWithItsStatus() throws Exception {
        mockMvc.perform(getJson("/api/reports/worker/{id}/days" + RANGE, ana.getUserId()).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workerId").value(ana.getUserId()))
                .andExpect(jsonPath("$.firstName").value("First ana"))
                .andExpect(jsonPath("$.from").value("2030-05-10"))
                .andExpect(jsonPath("$.plannedMinutes").value(900))
                .andExpect(jsonPath("$.workedMinutes").value(570))
                .andExpect(jsonPath("$.differenceMinutes").value(-330))
                .andExpect(jsonPath("$.plannedCost").value(164.0))
                .andExpect(jsonPath("$.actualCost").value(99.0))
                .andExpect(jsonPath("$.days[*].date", contains("2030-05-10", "2030-05-11", "2030-05-12", "2030-05-13")))
                .andExpect(jsonPath("$.days[*].status", contains("LATE", "MISSED", "UNPLANNED", "UPCOMING")))
                .andExpect(jsonPath("$.days[*].plannedMinutes", contains(480, 240, 0, 180)))
                .andExpect(jsonPath("$.days[*].workedMinutes", contains(450, 0, 120, 0)))
                .andExpect(jsonPath("$.days[0].shifts[0].id").value(anasFriday.getId()))
                .andExpect(jsonPath("$.days[0].shifts[0].endsNextDay").value(true))
                .andExpect(jsonPath("$.days[0].entries[0].shiftId").value(anasFriday.getId()))
                .andExpect(jsonPath("$.days[0].entries[0].workedMinutes").value(450))
                .andExpect(jsonPath("$.days[1].entries", hasSize(0)));

        mockMvc.perform(getJson("/api/reports/worker/{id}/days" + RANGE, ben.getUserId()).with(as(owner)))
                .andExpect(jsonPath("$.days[*].status", contains("OK", "OPEN")))
                .andExpect(jsonPath("$.plannedCost").value(nullValue()));
    }

    @Test
    void aShiftIsLateAtMostOnceAndOnlyByItsFirstEntry() throws Exception {
        Worker gus = createWorker(venue, "gus");
        // Fri: in late at 18:15, out for a break, back at 20:30. One late shift, not two
        WorkerShift friday = createShift(gus, bar, FRI, "18:00", "02:00");
        createTimeEntry(gus, friday, FRI, "2030-05-10T16:15:00Z", "2030-05-10T18:00:00Z", 0);
        createTimeEntry(gus, friday, FRI, "2030-05-10T18:30:00Z", "2030-05-11T00:00:00Z", 0);
        // Sat: in on time at 09:55, back at 11:30 after an errand. On time
        WorkerShift saturday = createShift(gus, bar, SAT, "10:00", "14:00");
        createTimeEntry(gus, saturday, SAT, "2030-05-11T07:55:00Z", "2030-05-11T09:00:00Z", 0);
        createTimeEntry(gus, saturday, SAT, "2030-05-11T09:30:00Z", "2030-05-11T12:00:00Z", 0);
        // Sun: the on-time entry was voided, so the first one that counts is the 09:30 one. Late
        WorkerShift sunday = createShift(gus, bar, SUN, "08:00", "12:00");
        TimeEntry voided = createTimeEntry(gus, sunday, SUN, "2030-05-12T06:00:00Z", "2030-05-12T07:00:00Z", 0);
        voided.setVoidedAt(Instant.parse("2030-05-12T07:05:00Z"));
        timeEntryRepository.save(voided);
        createTimeEntry(gus, sunday, SUN, "2030-05-12T07:30:00Z", "2030-05-12T09:30:00Z", 0);

        mockMvc.perform(getJson("/api/reports/establishment/{id}/hours" + RANGE, venue.getId()).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workers[?(@.firstName == 'First gus')].entriesCount", contains(5)))
                .andExpect(jsonPath("$.workers[?(@.firstName == 'First gus')].lateCount", contains(2)));
        mockMvc.perform(getJson("/api/reports/worker/{id}/days" + RANGE, gus.getUserId()).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[*].date", contains("2030-05-10", "2030-05-11", "2030-05-12")))
                .andExpect(jsonPath("$.days[*].status", contains("LATE", "OK", "LATE")));
    }

    @Test
    void aDayWithSeveralShiftsTakesTheStatusOfEachOfThem() throws Exception {
        Worker hugo = createWorker(venue, "hugo");
        // Fri: the morning shift was worked, the evening one was not. MISSED, not OK
        WorkerShift fridayMorning = createShift(hugo, bar, FRI, "09:00", "13:00");
        createTimeEntry(hugo, fridayMorning, FRI, "2030-05-10T07:00:00Z", "2030-05-10T11:00:00Z", 0);
        createShift(hugo, bar, FRI, "18:00", "23:00");
        // Sat: the shift was worked, and there is also an entry at 18:00 with no shift. UNPLANNED
        WorkerShift saturday = createShift(hugo, bar, SAT, "10:00", "14:00");
        createTimeEntry(hugo, saturday, SAT, "2030-05-11T08:00:00Z", "2030-05-11T12:00:00Z", 0);
        createTimeEntry(hugo, null, SAT, "2030-05-11T16:00:00Z", "2030-05-11T17:00:00Z", 0);
        // Sun: the morning shift was worked and the evening one has not started at 12:00. UPCOMING
        WorkerShift sundayMorning = createShift(hugo, bar, SUN, "08:00", "11:00");
        createTimeEntry(hugo, sundayMorning, SUN, "2030-05-12T06:00:00Z", "2030-05-12T09:00:00Z", 0);
        createShift(hugo, bar, SUN, "20:00", "23:00");

        mockMvc.perform(getJson("/api/reports/worker/{id}/days" + RANGE, hugo.getUserId()).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[*].date", contains("2030-05-10", "2030-05-11", "2030-05-12")))
                .andExpect(jsonPath("$.days[*].status", contains("MISSED", "UNPLANNED", "UPCOMING")));
        // The hours report counts the same missed shift and entry without a shift
        mockMvc.perform(getJson("/api/reports/establishment/{id}/hours" + RANGE, venue.getId()).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workers[?(@.firstName == 'First hugo')].missedShiftsCount", contains(1)))
                .andExpect(jsonPath("$.workers[?(@.firstName == 'First hugo')].unplannedCount", contains(1)));
    }

    @Test
    void aWorkerReadsTheirOwnDaysWithoutCosts() throws Exception {
        mockMvc.perform(getJson("/api/reports/worker/{id}/days" + RANGE, ana.getUserId()).with(as(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedMinutes").value(900))
                .andExpect(jsonPath("$.plannedCost").value(nullValue()))
                .andExpect(jsonPath("$.actualCost").value(nullValue()))
                .andExpect(jsonPath("$.days", hasSize(4)));

        mockMvc.perform(getJson("/api/reports/worker/{id}/days" + RANGE, ben.getUserId()).with(as(ana)))
                .andExpect(status().isForbidden());
        mockMvc.perform(getJson("/api/reports/establishment/{id}/hours" + RANGE, venue.getId()).with(as(ana)))
                .andExpect(status().isForbidden());
    }

    @Test
    void aCostIsUnknownWhenSomeMinutesFallBeforeTheFirstRate() throws Exception {
        rate(ben, "11.00", SAT);

        mockMvc.perform(getJson("/api/reports/establishment/{id}/hours" + RANGE, venue.getId()).with(as(owner)))
                .andExpect(jsonPath("$.workers[1].currentHourlyRate").value(11.0))
                // Friday's 300 planned and 295 worked minutes have no rate
                .andExpect(jsonPath("$.workers[1].plannedCost").value(nullValue()))
                .andExpect(jsonPath("$.workers[1].actualCost").value(nullValue()))
                // so the total leaves him out and says so
                .andExpect(jsonPath("$.totals.actualCost").value(99.0))
                .andExpect(jsonPath("$.totals.costPartial").value(true))
                .andExpect(jsonPath("$.totals.workersWithoutCost").value(1));

        // From Saturday on Ben only has the open entry, which has no minutes yet
        mockMvc.perform(getJson("/api/reports/establishment/{id}/hours?from=2030-05-11&to=2030-05-13", venue.getId())
                        .with(as(owner)))
                .andExpect(jsonPath("$.workers[1].plannedCost").value(0.0))
                .andExpect(jsonPath("$.workers[1].actualCost").value(0.0))
                .andExpect(jsonPath("$.totals.costPartial").value(false))
                .andExpect(jsonPath("$.totals.workersWithoutCost").value(0));
    }

    @Test
    void requiresABoundedRange() throws Exception {
        String hours = "/api/reports/establishment/" + venue.getId() + "/hours";

        mockMvc.perform(getJson(hours + "?from=2030-05-10").with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required parameter 'to'"));
        mockMvc.perform(getJson(hours + "?from=2030-05-11&to=2030-05-10").with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("'from' must not be after 'to'"));
        mockMvc.perform(getJson(hours + "?from=2030-01-01&to=2031-01-02").with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The date range cannot be longer than 366 days"));
        mockMvc.perform(getJson(hours + "?from=2030-01-01&to=2031-01-01").with(as(owner)))
                .andExpect(status().isOk());
        mockMvc.perform(getJson("/api/reports/worker/{id}/days?from=2030-01-01&to=2031-01-02", ana.getUserId())
                        .with(as(ana)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportsRunTheSameNumberOfQueriesHoweverMuchDataThereIs() throws Exception {
        long[] before = statementsPerReport();

        for (int i = 0; i < 4; i++) {
            Worker extra = createWorker(venue, uniqueName("extra"));
            rate(extra, "10.00", LocalDate.of(2030, 1, 1));
            for (int day = 0; day < 3; day++) {
                WorkerShift shift = createShift(extra, bar, FRI.plusDays(day), "10:00", "14:00");
                createTimeEntry(extra, shift, FRI.plusDays(day), FRI.plusDays(day) + "T08:00:00Z",
                        FRI.plusDays(day) + "T12:00:00Z", 0);
                createShift(ana, bar, FRI.plusDays(day), "06:00", "07:00");
            }
        }
        long[] after = statementsPerReport();

        assertTrue(before[0] > 0 && before[1] > 0, "statistics must be enabled in the test profile");
        assertEquals(before[0], after[0], "hours by establishment");
        assertEquals(before[1], after[1], "days of a worker");
    }

    private long[] statementsPerReport() throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        mockMvc.perform(getJson("/api/reports/establishment/{id}/hours" + RANGE, venue.getId()).with(as(owner)))
                .andExpect(status().isOk());
        long hours = statistics.getPrepareStatementCount();
        statistics.clear();
        mockMvc.perform(getJson("/api/reports/worker/{id}/days" + RANGE, ana.getUserId()).with(as(owner)))
                .andExpect(status().isOk());
        long days = statistics.getPrepareStatementCount();
        return new long[]{hours, days};
    }

    private Worker inactive(Worker worker) {
        worker.setActive(false);
        return workerRepository.save(worker);
    }

    private void rate(Worker worker, String hourlyRate, LocalDate effectiveFrom) {
        workerRateRepository.save(new WorkerRate(worker, new BigDecimal(hourlyRate), effectiveFrom, Instant.now()));
    }
}
