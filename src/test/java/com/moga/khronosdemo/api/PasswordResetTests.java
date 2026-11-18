package com.moga.khronosdemo.api;

import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PasswordResetTests extends IntegrationTest {

    private static final String NEW_PASSWORD = "brand-new-password";

    private User admin;
    private User owner;
    private User otherOwner;
    private Establishment venue;
    private Worker worker;
    private Worker otherWorker;

    @BeforeEach
    void setUp() {
        admin = createAdmin("admin");
        owner = createOwner("owner");
        otherOwner = createOwner("other-owner");
        venue = createEstablishment(owner);
        worker = createWorker(venue, "worker");
        otherWorker = createWorker(createEstablishment(otherOwner), "other-worker");
    }

    @Test
    void ownerResetsTheirWorkersPasswordAndOldTokensStopWorking() throws Exception {
        String oldToken = tokenFor(worker.getUserId());
        // Token times have one-second precision, so let the reset happen in a later second
        Thread.sleep(1100);

        reset(worker.getUserId(), as(owner)).andExpect(status().isNoContent());

        mockMvc.perform(getJson("/api/users/me").with(bearer(oldToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Your session has expired or is no longer valid. Sign in again"))
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
        login("worker", PASSWORD).andExpect(status().isUnauthorized());
        login("worker", NEW_PASSWORD).andExpect(status().isOk());
        assertTrue(passwordEncoder.matches(NEW_PASSWORD,
                userRepository.findById(worker.getUserId()).orElseThrow().getPassword()));
    }

    @Test
    void ownerCanOnlyResetTheirOwnWorkers() throws Exception {
        reset(otherWorker.getUserId(), as(owner)).andExpect(status().isForbidden());
        reset(otherOwner.getId(), as(owner)).andExpect(status().isForbidden());
        reset(admin.getId(), as(owner)).andExpect(status().isForbidden());
        reset(owner.getId(), as(owner)).andExpect(status().isForbidden());

        login("other-worker", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void adminResetsAnyoneButAnotherAdmin() throws Exception {
        User secondAdmin = createAdmin("second-admin");

        reset(owner.getId(), as(admin)).andExpect(status().isNoContent());
        reset(otherWorker.getUserId(), as(admin)).andExpect(status().isNoContent());
        reset(secondAdmin.getId(), as(admin)).andExpect(status().isForbidden());
        reset(admin.getId(), as(admin)).andExpect(status().isForbidden());
        reset(987654L, as(admin)).andExpect(status().isNotFound());

        login("owner", NEW_PASSWORD).andExpect(status().isOk());
        login("second-admin", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void workersCannotResetPasswords() throws Exception {
        Worker colleague = createWorker(venue, "colleague");

        reset(colleague.getUserId(), as(worker)).andExpect(status().isForbidden());
        reset(worker.getUserId(), as(worker)).andExpect(status().isForbidden());
    }

    @Test
    void newPasswordFollowsTheSameRulesAsOnCreation() throws Exception {
        mockMvc.perform(putJson("/api/users/{id}/password/reset", """
                        { "newPassword": "short" }
                        """, worker.getUserId()).with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newPassword").exists());
        mockMvc.perform(putJson("/api/users/{id}/password/reset", "{}", worker.getUserId()).with(as(owner)))
                .andExpect(status().isBadRequest());
    }

    private ResultActions reset(Long userId, RequestPostProcessor caller) throws Exception {
        return mockMvc.perform(putJson("/api/users/{id}/password/reset", """
                { "newPassword": "%s" }
                """.formatted(NEW_PASSWORD), userId).with(caller));
    }

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(postJson("/api/auth/login", """
                { "username": "%s", "password": "%s" }
                """.formatted(username, password)));
    }
}
