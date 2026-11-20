package com.moga.khronosdemo.api;

import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.entities.enums.Plan;
import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlanTests extends IntegrationTest {

    // ---- Features ----

    @Test
    void aBasicOwnerHasTheScheduleButNeitherTheCashNorTheInventory() throws Exception {
        User owner = createOwner("owner", Plan.BASIC);
        Establishment venue = createEstablishment(owner);

        mockMvc.perform(getJson("/api/zones/establishment/{id}", venue.getId()).with(as(owner)))
                .andExpect(status().isOk());
        mockMvc.perform(getJson("/api/reports/establishment/{id}/hours?from={from}&to={to}", venue.getId(),
                        LocalDate.now().minusDays(6), LocalDate.now()).with(as(owner)))
                .andExpect(status().isOk());

        refused(getCash(venue, as(owner)), "CASH", "BASIC");
        refused(mockMvc.perform(getJson("/api/expenses/establishment/{id}", venue.getId()).with(as(owner))), "CASH", "BASIC");
        refused(getSummary(venue, as(owner)), "CASH", "BASIC");
        refused(getProducts(venue, as(owner)), "INVENTORY", "BASIC");
        refused(mockMvc.perform(getJson("/api/suppliers/establishment/{id}", venue.getId()).with(as(owner))), "INVENTORY", "BASIC");
        refused(mockMvc.perform(getJson("/api/purchase-orders/establishment/{id}", venue.getId()).with(as(owner))), "INVENTORY", "BASIC");
        refused(mockMvc.perform(getJson("/api/reports/establishment/{id}/inventory.csv", venue.getId()).with(as(owner))), "INVENTORY", "BASIC");
    }

    @Test
    void aPlusOwnerHasTheCashButNotTheInventory() throws Exception {
        User owner = createOwner("owner", Plan.PLUS);
        Establishment venue = createEstablishment(owner);

        getCash(venue, as(owner)).andExpect(status().isOk());
        getSummary(venue, as(owner)).andExpect(status().isOk());
        refused(getProducts(venue, as(owner)), "INVENTORY", "PLUS");
    }

    @Test
    void anUltraOwnerHasEverything() throws Exception {
        User owner = createOwner("owner", Plan.ULTRA);
        Establishment venue = createEstablishment(owner);

        getCash(venue, as(owner)).andExpect(status().isOk());
        getProducts(venue, as(owner)).andExpect(status().isOk());
    }

    // The 403 comes before the body is read, so an invalid body still gets it
    @Test
    void theRefusalComesBeforeValidation() throws Exception {
        User owner = createOwner("owner", Plan.BASIC);
        createEstablishment(owner);

        refused(mockMvc.perform(postJson("/api/products", "{}").with(as(owner))), "INVENTORY", "BASIC");
    }

    @Test
    void aPlatformAdminIsNeverLimitedByTheOwnersPlan() throws Exception {
        Establishment venue = createEstablishment(createOwner("owner", Plan.BASIC));

        getCash(venue, as(createAdmin("admin"))).andExpect(status().isOk());
    }

    @Test
    void aWorkerStillGetsThePlainRefusal() throws Exception {
        Establishment venue = createEstablishment(createOwner("owner", Plan.BASIC));
        Worker worker = createWorker(venue, "worker");

        getCash(venue, as(worker))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void aChangeOfPlanAppliesFromTheNextRequestWithTheSameToken() throws Exception {
        User owner = createOwner("owner", Plan.BASIC);
        Establishment venue = createEstablishment(owner);
        String token = tokenFor(owner.getId());

        refused(getCash(venue, bearer(token)), "CASH", "BASIC");
        changePlan(owner, "PLUS", as(createAdmin("admin"))).andExpect(status().isOk());
        getCash(venue, bearer(token)).andExpect(status().isOk());
    }

    // ---- Worker limit ----

    @Test
    void aBasicOwnerCanHave50ActiveWorkersAcrossAllTheirVenues() throws Exception {
        User owner = createOwner("owner", Plan.BASIC);
        Establishment first = createEstablishment(owner);
        Establishment second = createEstablishment(owner);
        for (int i = 0; i < 49; i++) {
            createWorker(i % 2 == 0 ? first : second, "worker-" + i);
        }

        addWorker(second, "worker-49", as(owner)).andExpect(status().isCreated());
        addWorker(first, "worker-50", as(owner))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORKER_LIMIT_REACHED"))
                .andExpect(jsonPath("$.details.plan").value("BASIC"))
                .andExpect(jsonPath("$.details.maxActiveWorkers").value("50"))
                .andExpect(jsonPath("$.details.activeWorkers").value("50"));

        // The refused request left no account behind
        assertEquals(0, jdbcTemplate.queryForObject(
                "select count(*) from users where username = 'worker-50'", Integer.class));
    }

    @Test
    void inactiveWorkersDoNotCountButReactivatingThemDoes() throws Exception {
        User owner = createOwner("owner", Plan.BASIC);
        Establishment venue = createEstablishment(owner);
        Worker former = createWorker(venue, "former");
        former.setActive(false);
        workerRepository.save(former);
        for (int i = 0; i < 50; i++) {
            createWorker(venue, "worker-" + i);
        }

        setActive(former, true, as(owner))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORKER_LIMIT_REACHED"));

        // Deactivating always works, and frees a place
        Worker leaving = workerRepository.findByEstablishment_Id(venue.getId()).stream()
                .filter(Worker::getActive).findFirst().orElseThrow();
        setActive(leaving, false, as(owner)).andExpect(status().isOk());
        setActive(former, true, as(owner)).andExpect(status().isOk());
    }

    @Test
    void anUltraOwnerHasNoWorkerLimit() throws Exception {
        User owner = createOwner("owner", Plan.ULTRA);
        Establishment venue = createEstablishment(owner);
        for (int i = 0; i < 101; i++) {
            createWorker(venue, "worker-" + i);
        }

        addWorker(venue, "one-more", as(owner)).andExpect(status().isCreated());
    }

    // ---- Changing and reading the plan ----

    @Test
    void anOwnerCannotMoveToAPlanTheirActiveWorkersDoNotFit() throws Exception {
        User owner = createOwner("owner", Plan.PLUS);
        Establishment venue = createEstablishment(owner);
        for (int i = 0; i < 51; i++) {
            createWorker(venue, "worker-" + i);
        }
        User admin = createAdmin("admin");

        changePlan(owner, "BASIC", as(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORKER_LIMIT_REACHED"))
                .andExpect(jsonPath("$.details.activeWorkers").value("51"));
        changePlan(owner, "ULTRA", as(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("ULTRA"))
                .andExpect(jsonPath("$.maxActiveWorkers").doesNotExist())
                .andExpect(jsonPath("$.activeWorkers").value(51))
                .andExpect(jsonPath("$.features[0]").value("CASH"))
                .andExpect(jsonPath("$.features[1]").value("INVENTORY"));
    }

    @Test
    void onlyAPlatformAdminChangesPlansAndOnlyOwnersHaveOne() throws Exception {
        User owner = createOwner("owner", Plan.BASIC);
        Worker worker = createWorker(createEstablishment(owner), "worker");
        User admin = createAdmin("admin");

        changePlan(owner, "ULTRA", as(owner)).andExpect(status().isForbidden());
        changePlan(worker.getUser(), "ULTRA", as(admin)).andExpect(status().isConflict());
        mockMvc.perform(putJson("/api/users/{id}/plan", "{}", owner.getId()).with(as(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.plan").exists());
    }

    @Test
    void anOwnerSeesTheirOwnPlanAndUsage() throws Exception {
        User owner = createOwner("owner", Plan.PLUS);
        createWorker(createEstablishment(owner), "worker");

        mockMvc.perform(getJson("/api/users/me").with(as(owner)))
                .andExpect(jsonPath("$.plan").value("PLUS"));
        mockMvc.perform(getJson("/api/users/{id}/plan", owner.getId()).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("PLUS"))
                .andExpect(jsonPath("$.maxActiveWorkers").value(100))
                .andExpect(jsonPath("$.activeWorkers").value(1))
                .andExpect(jsonPath("$.features.length()").value(1));
        mockMvc.perform(getJson("/api/users/{id}/plan", owner.getId()).with(as(createOwner("other"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void aNewOwnerStartsOnBasicUnlessTheAdminPicksAPlan() throws Exception {
        User admin = createAdmin("admin");

        createOwnerAccount("plain", "", as(admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plan").value("BASIC"));
        createOwnerAccount("plus", "&plan=PLUS", as(admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plan").value("PLUS"));
        mockMvc.perform(postJson("/api/users/create?role=WORKER&plan=PLUS", userJson("worker")).with(as(admin)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(postJson("/api/users/create?role=WORKER", userJson("worker")).with(as(admin)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plan").doesNotExist());
    }

    // ---- Helpers ----

    private static void refused(ResultActions result, String feature, String plan) throws Exception {
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLAN_FEATURE_UNAVAILABLE"))
                .andExpect(jsonPath("$.details.feature").value(feature))
                .andExpect(jsonPath("$.details.plan").value(plan));
    }

    private ResultActions getCash(Establishment venue, RequestPostProcessor caller) throws Exception {
        return mockMvc.perform(getJson("/api/cash-closings/establishment/{id}", venue.getId()).with(caller));
    }

    private ResultActions getSummary(Establishment venue, RequestPostProcessor caller) throws Exception {
        return mockMvc.perform(getJson("/api/reports/establishment/{id}/summary?from={from}&to={to}", venue.getId(),
                LocalDate.now().minusDays(6), LocalDate.now()).with(caller));
    }

    private ResultActions getProducts(Establishment venue, RequestPostProcessor caller) throws Exception {
        return mockMvc.perform(getJson("/api/products/establishment/{id}", venue.getId()).with(caller));
    }

    private ResultActions changePlan(User owner, String plan, RequestPostProcessor caller) throws Exception {
        return mockMvc.perform(putJson("/api/users/{id}/plan", "{\"plan\":\"" + plan + "\"}", owner.getId()).with(caller));
    }

    private ResultActions setActive(Worker worker, boolean active, RequestPostProcessor caller) throws Exception {
        return mockMvc.perform(putJson("/api/workers/{id}/active", "{\"active\":" + active + "}", worker.getUserId())
                .with(caller));
    }

    private ResultActions addWorker(Establishment venue, String username, RequestPostProcessor caller) throws Exception {
        String body = "{\"establishmentId\":" + venue.getId() + ",\"user\":" + userJson(username) + "}";
        return mockMvc.perform(postJson("/api/workers/with-user", body).with(caller));
    }

    private ResultActions createOwnerAccount(String username, String planQuery, RequestPostProcessor caller) throws Exception {
        return mockMvc.perform(postJson("/api/users/create?role=OWNER" + planQuery, userJson(username)).with(caller));
    }

    private static String userJson(String username) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\",\"firstName\":\"First\","
                + "\"lastName\":\"Last\",\"email\":\"" + username + "@example.test\"}";
    }
}
