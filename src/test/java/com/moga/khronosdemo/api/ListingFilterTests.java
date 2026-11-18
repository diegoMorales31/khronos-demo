package com.moga.khronosdemo.api;

import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.entities.Zone;
import com.moga.khronosdemo.support.IntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ListingFilterTests extends IntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2030, 5, 10);

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private User owner;
    private Establishment venue;
    private Zone hall;
    private Zone terrace;
    private Worker worker;
    private Worker colleague;

    @BeforeEach
    void setUp() {
        owner = createOwner("owner");
        venue = createEstablishment(owner);
        hall = createZone(venue, "Hall");
        terrace = createZone(venue, "Terrace");
        worker = createWorker(venue, "worker");
        colleague = createWorker(venue, "colleague");
    }

    @Test
    void shiftListingsReturnEverythingInOrderWithoutDates() throws Exception {
        createShift(worker, hall, DAY.plusDays(1), "09:00", "12:00");
        createShift(worker, hall, DAY, "18:00", "22:00");
        createShift(worker, hall, DAY, "09:00", "12:00");
        createShift(colleague, terrace, DAY, "10:00", "14:00");

        get("/api/worker-shifts/worker/{id}", worker.getUserId(), "")
                .andExpect(jsonPath("$[*].date", contains("2030-05-10", "2030-05-10", "2030-05-11")))
                .andExpect(jsonPath("$[*].startTime", contains("09:00:00", "18:00:00", "09:00:00")));
        get("/api/worker-shifts/owner/{id}", owner.getId(), "").andExpect(jsonPath("$", hasSize(4)));
        get("/api/worker-shifts/zone/{id}", terrace.getId(), "").andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void shiftListingsKeepDatesFromFromToToInclusive() throws Exception {
        for (int offset = -1; offset <= 2; offset++) {
            createShift(worker, hall, DAY.plusDays(offset), "09:00", "12:00");
        }
        createShift(colleague, terrace, DAY, "09:00", "12:00");

        get("/api/worker-shifts/worker/{id}", worker.getUserId(), "?from=2030-05-10&to=2030-05-11")
                .andExpect(jsonPath("$[*].date", contains("2030-05-10", "2030-05-11")));
        get("/api/worker-shifts/worker/{id}", worker.getUserId(), "?from=2030-05-11")
                .andExpect(jsonPath("$[*].date", contains("2030-05-11", "2030-05-12")));
        get("/api/worker-shifts/worker/{id}", worker.getUserId(), "?to=2030-05-09")
                .andExpect(jsonPath("$[*].date", contains("2030-05-09")));
        get("/api/worker-shifts/owner/{id}", owner.getId(), "?from=2030-05-10&to=2030-05-10")
                .andExpect(jsonPath("$", hasSize(2)));
        get("/api/worker-shifts/zone/{id}", hall.getId(), "?from=2030-05-12&to=2030-05-31")
                .andExpect(jsonPath("$[*].date", contains("2030-05-12")));
    }

    @Test
    void eventListingsFilterOnTheEventDate() throws Exception {
        createEvent(hall, DAY.minusDays(1).atTime(23, 59));
        createEvent(hall, DAY.atStartOfDay());
        createEvent(terrace, DAY.atTime(23, 59, 59));
        createEvent(hall, DAY.plusDays(1).atStartOfDay());

        get("/api/events/establishment/{id}", venue.getId(), "?from=2030-05-10&to=2030-05-10")
                .andExpect(jsonPath("$[*].dateTime", contains("2030-05-10T00:00:00", "2030-05-10T23:59:59")))
                .andExpect(jsonPath("$[1].zoneName").value("Terrace"));
        get("/api/events/zone/{id}", hall.getId(), "?from=2030-05-10")
                .andExpect(jsonPath("$[*].dateTime", contains("2030-05-10T00:00:00", "2030-05-11T00:00:00")));
        get("/api/events/zone/{id}", hall.getId(), "?to=2030-05-09")
                .andExpect(jsonPath("$[*].dateTime", contains("2030-05-09T23:59:00")));
        get("/api/events/establishment/{id}", venue.getId(), "").andExpect(jsonPath("$", hasSize(4)));
    }

    @Test
    void rejectsAReversedOrMalformedRange() throws Exception {
        mockMvc.perform(getJson("/api/worker-shifts/worker/{id}?from=2030-05-11&to=2030-05-10", worker.getUserId())
                        .with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("'from' must not be after 'to'"));
        mockMvc.perform(getJson("/api/events/zone/{id}?from=10-05-2030", hall.getId()).with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value '10-05-2030' for parameter 'from'"));
    }

    @Test
    void listingsDoNotRunAQueryPerRow() throws Exception {
        createShift(worker, hall, DAY, "09:00", "12:00");
        createEvent(hall, DAY.atTime(10, 0));
        long[] withOneRow = statementsPerListing();

        for (int i = 0; i < 4; i++) {
            Worker another = createWorker(venue, uniqueName("extra"));
            Zone zone = createZone(venue, uniqueName("Zone"));
            createShift(another, zone, DAY, "09:00", "12:00");
            createEvent(zone, DAY.atTime(10, 0));
        }
        long[] withManyRows = statementsPerListing();

        assertTrue(withOneRow[0] > 0 && withOneRow[1] > 0, "statistics must be enabled in the test profile");
        assertEquals(withOneRow[0], withManyRows[0], "shifts by owner");
        assertEquals(withOneRow[1], withManyRows[1], "events by establishment");
    }

    private long[] statementsPerListing() throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        get("/api/worker-shifts/owner/{id}", owner.getId(), "");
        long shifts = statistics.getPrepareStatementCount();
        statistics.clear();
        get("/api/events/establishment/{id}", venue.getId(), "");
        long events = statistics.getPrepareStatementCount();
        return new long[]{shifts, events};
    }

    private ResultActions get(String path, Long id, String query) throws Exception {
        return mockMvc.perform(getJson(path + query, id).with(as(owner))).andExpect(status().isOk());
    }
}
