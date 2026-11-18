package com.moga.khronosdemo.api;

import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkerActivationTests extends IntegrationTest {

    private User owner;
    private Worker worker;

    @BeforeEach
    void setUp() {
        owner = createOwner("owner");
        Establishment venue = createEstablishment(owner);
        worker = createWorker(venue, "worker");
    }

    @Test
    void setsTheFlagToTheGivenValueEveryTime() throws Exception {
        setActive(false, as(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(worker.getUserId()))
                .andExpect(jsonPath("$.isActive").value(false));
        setActive(false, as(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));
        assertFalse(isActive());

        setActive(true, as(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(true));
        setActive(true, as(owner))
                .andExpect(jsonPath("$.isActive").value(true));
        assertTrue(isActive());
    }

    @Test
    void aDeactivatedWorkersTokenIsRejectedAtOnce() throws Exception {
        String token = tokenFor(worker.getUserId());

        setActive(false, as(owner)).andExpect(status().isOk());

        mockMvc.perform(getJson("/api/users/me").with(bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("This account is disabled"))
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void onlyTheOwnerOrAnAdminCanChangeIt() throws Exception {
        User otherOwner = createOwner("other-owner");

        setActive(false, as(otherOwner)).andExpect(status().isForbidden());
        setActive(false, as(worker)).andExpect(status().isForbidden());
        setActive(false, as(createAdmin("admin"))).andExpect(status().isOk());
    }

    @Test
    void requiresTheFlag() throws Exception {
        mockMvc.perform(putJson("/api/workers/{id}/active", "{}", worker.getUserId()).with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.active").exists());
    }

    @Test
    void theDeprecatedEndpointStillToggles() throws Exception {
        mockMvc.perform(deleteJson("/api/workers/deactivate/{id}", worker.getUserId()).with(as(owner)))
                .andExpect(status().isNoContent());
        assertFalse(isActive());

        mockMvc.perform(deleteJson("/api/workers/deactivate/{id}", worker.getUserId()).with(as(owner)))
                .andExpect(status().isNoContent());
        assertTrue(isActive());
    }

    private ResultActions setActive(boolean active, RequestPostProcessor caller) throws Exception {
        return mockMvc.perform(putJson("/api/workers/{id}/active", """
                { "active": %s }
                """.formatted(active), worker.getUserId()).with(caller));
    }

    private boolean isActive() {
        return workerRepository.findById(worker.getUserId()).orElseThrow().getActive();
    }
}
