package com.moga.khronosdemo.api;

import com.jayway.jsonpath.JsonPath;
import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sessions that outlive the access token: a refresh token from the login is swapped for a new pair as
 * often as needed, until the device signs out, the password changes, the worker is deactivated, or it
 * goes unused for the idle time. A replaced token used again ends the session.
 */
class SessionRefreshTests extends IntegrationTest {

    private User owner;
    private Worker worker;

    @BeforeEach
    void setUp() {
        owner = createOwner("owner");
        Establishment venue = createEstablishment(owner);
        worker = createWorker(venue, "worker");
    }

    private String loginRefreshToken(String username) throws Exception {
        String body = mockMvc.perform(postJson("/api/auth/login", """
                        { "username": "%s", "password": "%s" }
                        """.formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.refreshToken");
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(postJson("/api/auth/refresh", """
                { "refreshToken": "%s" }
                """.formatted(refreshToken)));
    }

    private String refreshed(String refreshToken) throws Exception {
        return JsonPath.read(refresh(refreshToken).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "$.refreshToken");
    }

    @Test
    void aRefreshTokenGivesANewAccessTokenAndReplacesItself() throws Exception {
        String first = loginRefreshToken("worker");

        String body = refresh(first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andReturn().getResponse().getContentAsString();
        String accessToken = JsonPath.read(body, "$.accessToken");
        String second = JsonPath.read(body, "$.refreshToken");
        assertNotEquals(first, second);

        mockMvc.perform(getJson("/api/users/me").with(bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("worker"));
        refreshed(second);
    }

    @Test
    void onlyAHashOfTheTokenIsStored() throws Exception {
        String token = loginRefreshToken("worker");

        assertEquals(0, jdbcTemplate.queryForObject("select count(*) from refresh_sessions where token_hash = ?",
                Integer.class, token));
        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from refresh_sessions", Integer.class));
    }

    @Test
    void aReplacedTokenUsedAgainLaterEndsTheSession() throws Exception {
        String stolen = loginRefreshToken("worker");
        String current = refreshed(stolen);
        clock.advance(Duration.ofMinutes(5));

        refresh(stolen).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
        refresh(current).andExpect(status().isUnauthorized());
    }

    @Test
    void aReplacedTokenSentAgainRightAwayIsRefusedButTheSessionGoesOn() throws Exception {
        String first = loginRefreshToken("worker");
        String second = refreshed(first);

        // A second tab, or a retry after a lost answer
        refresh(first).andExpect(status().isUnauthorized());
        refreshed(second);
    }

    @Test
    void aDeviceThatSignsOutCannotRefreshAgain() throws Exception {
        String token = loginRefreshToken("worker");
        String otherDevice = loginRefreshToken("worker");

        mockMvc.perform(postJson("/api/auth/logout", """
                        { "refreshToken": "%s" }
                        """.formatted(token)))
                .andExpect(status().isNoContent());

        refresh(token).andExpect(status().isUnauthorized());
        refreshed(otherDevice);
    }

    @Test
    void aSessionUnusedForTheIdleTimeEnds_butUseKeepsItGoing() throws Exception {
        String token = loginRefreshToken("worker");
        clock.advance(Duration.ofDays(80));
        token = refreshed(token);
        clock.advance(Duration.ofDays(80));
        token = refreshed(token);

        clock.advance(Duration.ofDays(91));
        refresh(token).andExpect(status().isUnauthorized());
    }

    @Test
    void changingOrResettingThePasswordEndsEverySession() throws Exception {
        String token = loginRefreshToken("worker");
        mockMvc.perform(putJson("/api/users/{id}/password", """
                        { "currentPassword": "%s", "newPassword": "a-new-password-42" }
                        """.formatted(PASSWORD), worker.getUserId()).with(as(worker)))
                .andExpect(status().is2xxSuccessful());
        refresh(token).andExpect(status().isUnauthorized());

        String ownerToken = loginRefreshToken("owner");
        String workerToken = JsonPath.read(mockMvc.perform(postJson("/api/auth/login", """
                        { "username": "worker", "password": "a-new-password-42" }
                        """)).andReturn().getResponse().getContentAsString(), "$.refreshToken");
        mockMvc.perform(putJson("/api/users/{id}/password/reset", """
                        { "newPassword": "another-password-42" }
                        """, worker.getUserId()).with(as(owner)))
                .andExpect(status().is2xxSuccessful());
        refresh(workerToken).andExpect(status().isUnauthorized());
        refreshed(ownerToken);
    }

    @Test
    void aDeactivatedWorkerCannotRefresh() throws Exception {
        String token = loginRefreshToken("worker");

        mockMvc.perform(putJson("/api/workers/{id}/active", """
                        { "active": false }
                        """, worker.getUserId()).with(as(owner)))
                .andExpect(status().isOk());

        refresh(token).andExpect(status().isUnauthorized());
    }

    @Test
    void anUnknownOrMissingTokenIsRefused() throws Exception {
        refresh("not-a-token").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
        mockMvc.perform(postJson("/api/auth/refresh", "{}")).andExpect(status().isBadRequest());
    }
}
