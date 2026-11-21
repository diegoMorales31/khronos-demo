package com.moga.khronosdemo.api;

import com.jayway.jsonpath.JsonPath;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthTests extends IntegrationTest {

    @Autowired
    private JwtEncoder jwtEncoder;

    @Test
    void signsInAndUsesTheTokenOnTheNextRequest() throws Exception {
        createOwner("ana");

        MvcResult result = login("ana", PASSWORD, "198.51.100.1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value(notNullValue()))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andReturn();
        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(getJson("/api/users/me").with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("ana"));
    }

    @Test
    void rejectsAWrongPasswordOrUnknownUserWithTheSameError() throws Exception {
        createOwner("ana");

        login("ana", "wrong-password", "198.51.100.2")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid username or password"))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        login("nobody", PASSWORD, "198.51.100.2")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void requestsWithoutAValidTokenAreRejected() throws Exception {
        mockMvc.perform(getJson("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required to access this resource"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(getJson("/api/users/me").with(bearer("not-a-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
    }

    @Test
    void anExpiredTokenIsReportedAsAnExpiredSession() throws Exception {
        User ana = createOwner("ana");
        Instant issuedAt = Instant.now().minus(Duration.ofHours(2));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("khronos")
                .subject(String.valueOf(ana.getId()))
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(Duration.ofHours(1)))
                .claim("role", ana.getRole().name())
                .build();
        String token = jwtEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        mockMvc.perform(getJson("/api/users/me").with(bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Your session has expired or is no longer valid. Sign in again"))
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
    }

    @Test
    void otherErrorsHaveNoCode() throws Exception {
        mockMvc.perform(postJson("/api/auth/login", "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").exists())
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void aDeactivatedWorkerCannotSignIn() throws Exception {
        deactivatedWorker("lucia");

        login("lucia", PASSWORD, "198.51.100.3")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("This account is disabled"))
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void aWrongPasswordDoesNotRevealThatAnAccountIsDeactivated() throws Exception {
        deactivatedWorker("lucia");
        createOwner("ana");
        String ip = "198.51.100.4";

        Map<String, Object> deactivated = bodyWithoutTimestamp(login("lucia", "wrong-password", ip)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS")));
        Map<String, Object> active = bodyWithoutTimestamp(login("ana", "wrong-password", ip)
                .andExpect(status().isUnauthorized()));
        Map<String, Object> unknown = bodyWithoutTimestamp(login("nobody", "wrong-password", ip)
                .andExpect(status().isUnauthorized()));

        assertEquals(active, deactivated);
        assertEquals(unknown, deactivated);
    }

    @Test
    void failedSignInsAgainstADeactivatedAccountCountTowardsTheLimit() throws Exception {
        deactivatedWorker("lucia");
        String ip = "198.51.100.5";

        for (int i = 0; i < 5; i++) {
            login("lucia", "wrong-password", ip).andExpect(status().isUnauthorized());
        }

        login("lucia", PASSWORD, ip).andExpect(status().isTooManyRequests());
    }

    @Test
    void blocksAUsernameAndIpAfterFiveFailures() throws Exception {
        createOwner("target");
        String ip = "203.0.113.10";

        for (int i = 0; i < 5; i++) {
            login("target", "wrong-password", ip).andExpect(status().isUnauthorized());
        }

        MvcResult blocked = login("target", "wrong-password", ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.message").value(startsWith("Too many failed sign-in attempts")))
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"))
                .andExpect(jsonPath("$.path").value("/api/auth/login"))
                .andReturn();
        long retryAfter = Long.parseLong(blocked.getResponse().getHeader(HttpHeaders.RETRY_AFTER));
        assertTrue(retryAfter > 0 && retryAfter <= 15 * 60, "Retry-After was " + retryAfter);

        // Even the right password is refused while blocked...
        login("target", PASSWORD, ip).andExpect(status().isTooManyRequests());
        // ...but the same user from another address is not affected
        login("target", PASSWORD, "203.0.113.11").andExpect(status().isOk());
    }

    @Test
    void blocksAnIpThatSpraysMadeUpUsernamesToGetFreshAttempts() throws Exception {
        createOwner("victim");
        String ip = "203.0.113.30";

        for (int i = 0; i < 5; i++) {
            login("victim", "wrong-password", ip).andExpect(status().isUnauthorized());
        }
        login("victim", "wrong-password", ip).andExpect(status().isTooManyRequests());

        // Failing with made-up usernames, to push the blocked pair out of memory, stops at 50 failures
        // for the address...
        for (int i = 0; i < 45; i++) {
            login("made-up-" + i, "wrong-password", ip).andExpect(status().isUnauthorized());
        }
        login("made-up-45", "wrong-password", ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));

        // ...so the address stays blocked, even with the right password, while others are not affected
        login("victim", PASSWORD, ip).andExpect(status().isTooManyRequests());
        login("victim", PASSWORD, "203.0.113.31").andExpect(status().isOk());
    }

    @Test
    void aSuccessfulSignInClearsEarlierFailures() throws Exception {
        createOwner("forgetful");
        String ip = "203.0.113.20";

        for (int i = 0; i < 4; i++) {
            login("forgetful", "wrong-password", ip).andExpect(status().isUnauthorized());
        }
        login("forgetful", PASSWORD, ip).andExpect(status().isOk());
        for (int i = 0; i < 4; i++) {
            login("forgetful", "wrong-password", ip).andExpect(status().isUnauthorized());
        }
        login("forgetful", PASSWORD, ip).andExpect(status().isOk());
    }

    @Test
    void blocksAUsernameGuessedFromManyIpsButNotFromWhereItSignedInBefore() throws Exception {
        createAdmin("spread-target");
        String home = "203.0.113.40";
        login("spread-target", PASSWORD, home).andExpect(status().isOk());

        // Ten guesses, one from each address: no pair and no address reaches its own limit...
        for (int i = 1; i <= 10; i++) {
            login("spread-target", "wrong-password", "198.18.0." + i).andExpect(status().isUnauthorized());
        }

        // ...but the username does, so a new address is refused even with the right password...
        login("spread-target", PASSWORD, "198.18.0.11")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
        // ...while the address it signed in from before still gets in, so the attack cannot lock the user out
        login("spread-target", PASSWORD, home).andExpect(status().isOk());
    }

    private void deactivatedWorker(String username) {
        Worker worker = createWorker(createEstablishment(createOwner(uniqueName("owner"))), username);
        worker.setActive(false);
        workerRepository.save(worker);
    }

    private static Map<String, Object> bodyWithoutTimestamp(ResultActions result) throws Exception {
        Map<String, Object> body = JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$");
        body.remove("timestamp");
        return body;
    }

    private ResultActions login(String username, String password, String ip) throws Exception {
        return mockMvc.perform(postJson("/api/auth/login", """
                { "username": "%s", "password": "%s" }
                """.formatted(username, password)).with(from(ip)));
    }

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
