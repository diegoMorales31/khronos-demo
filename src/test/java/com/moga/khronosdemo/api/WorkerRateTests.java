package com.moga.khronosdemo.api;

import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkerRateTests extends IntegrationTest {

    private User owner;
    private Worker worker;
    private Worker colleague;

    @BeforeEach
    void setUp() {
        owner = createOwner("owner");
        Establishment venue = createEstablishment(owner);
        worker = createWorker(venue, "worker");
        colleague = createWorker(venue, "colleague");
    }

    @Test
    void addsRatesAndListsTheMostRecentFirst() throws Exception {
        clock.setNow(Instant.parse("2030-05-01T09:30:00Z"));

        mockMvc.perform(postJson("/api/workers/{id}/rates", """
                        { "hourlyRate": 12.5, "effectiveFrom": "2030-01-01" }
                        """, worker.getUserId()).with(as(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.workerId").value(worker.getUserId()))
                .andExpect(jsonPath("$.hourlyRate").value(12.5))
                .andExpect(jsonPath("$.effectiveFrom").value("2030-01-01"))
                .andExpect(jsonPath("$.createdAt").value("2030-05-01T09:30:00Z"));
        mockMvc.perform(postJson("/api/workers/{id}/rates", """
                        { "hourlyRate": 14.25, "effectiveFrom": "2030-06-01" }
                        """, worker.getUserId()).with(as(owner)))
                .andExpect(status().isCreated());
        mockMvc.perform(postJson("/api/workers/{id}/rates", """
                        { "hourlyRate": 11, "effectiveFrom": "2030-01-01" }
                        """, colleague.getUserId()).with(as(owner)))
                .andExpect(status().isCreated());

        mockMvc.perform(getJson("/api/workers/{id}/rates", worker.getUserId()).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].effectiveFrom", contains("2030-06-01", "2030-01-01")))
                .andExpect(jsonPath("$[*].hourlyRate", contains(14.25, 12.5)));
    }

    @Test
    void rejectsASecondRateForTheSameDay() throws Exception {
        mockMvc.perform(postJson("/api/workers/{id}/rates", """
                        { "hourlyRate": 12.00, "effectiveFrom": "2030-01-01" }
                        """, worker.getUserId()).with(as(owner)))
                .andExpect(status().isCreated());

        mockMvc.perform(postJson("/api/workers/{id}/rates", """
                        { "hourlyRate": 13.00, "effectiveFrom": "2030-01-01" }
                        """, worker.getUserId()).with(as(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The worker already has a rate effective from 2030-01-01"));

        assertEquals(1, workerRateRepository.count());
    }

    @Test
    void validatesTheAmountAndTheDate() throws Exception {
        for (String amount : new String[]{"0", "-3", "12.345"}) {
            mockMvc.perform(postJson("/api/workers/{id}/rates", """
                            { "hourlyRate": %s, "effectiveFrom": "2030-01-01" }
                            """.formatted(amount), worker.getUserId()).with(as(owner)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.hourlyRate").exists());
        }
        mockMvc.perform(postJson("/api/workers/{id}/rates", """
                        { "hourlyRate": 12 }
                        """, worker.getUserId()).with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.effectiveFrom").value("must not be null"));

        assertEquals(0, workerRateRepository.count());
    }

    @Test
    void deletesARateOnlyThroughItsOwnWorker() throws Exception {
        clock.setNow(Instant.parse("2029-12-01T09:30:00Z"));
        Long rateId = longAt(mockMvc.perform(postJson("/api/workers/{id}/rates", """
                        { "hourlyRate": 12, "effectiveFrom": "2030-01-01" }
                        """, worker.getUserId()).with(as(owner)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");

        mockMvc.perform(deleteJson("/api/workers/{id}/rates/{rateId}", colleague.getUserId(), rateId).with(as(owner)))
                .andExpect(status().isNotFound());
        mockMvc.perform(deleteJson("/api/workers/{id}/rates/{rateId}", worker.getUserId(), rateId).with(as(owner)))
                .andExpect(status().isNoContent());

        assertEquals(0, workerRateRepository.count());
    }

    @Test
    void onlyARateThatHasNotTakenEffectYetCanBeDeleted() throws Exception {
        // 23:30 UTC on 31 December is already 1 January in Madrid, the venue's time zone
        clock.setNow(Instant.parse("2029-12-31T23:30:00Z"));
        Long past = addRate("2029-06-01");
        Long today = addRate("2030-01-01");
        Long tomorrow = addRate("2030-01-02");

        for (Long inForce : new Long[]{past, today}) {
            mockMvc.perform(deleteJson("/api/workers/{id}/rates/{rateId}", worker.getUserId(), inForce).with(as(owner)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Cannot delete a rate that has already taken effect;"
                            + " add a new rate from the day the change applies instead"));
        }
        mockMvc.perform(deleteJson("/api/workers/{id}/rates/{rateId}", worker.getUserId(), tomorrow).with(as(owner)))
                .andExpect(status().isNoContent());

        mockMvc.perform(getJson("/api/workers/{id}/rates", worker.getUserId()).with(as(owner)))
                .andExpect(jsonPath("$[*].effectiveFrom", contains("2030-01-01", "2029-06-01")));
    }

    @Test
    void aWorkerCannotSeeOrChangeRatesNotEvenTheirOwn() throws Exception {
        mockMvc.perform(getJson("/api/workers/{id}/rates", worker.getUserId()).with(as(worker)))
                .andExpect(status().isForbidden());
        mockMvc.perform(postJson("/api/workers/{id}/rates", """
                        { "hourlyRate": 99, "effectiveFrom": "2030-01-01" }
                        """, worker.getUserId()).with(as(worker)))
                .andExpect(status().isForbidden());

        assertEquals(0, workerRateRepository.count());
    }

    private Long addRate(String effectiveFrom) throws Exception {
        return longAt(mockMvc.perform(postJson("/api/workers/{id}/rates", """
                        { "hourlyRate": 12, "effectiveFrom": "%s" }
                        """.formatted(effectiveFrom), worker.getUserId()).with(as(owner)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
    }
}
