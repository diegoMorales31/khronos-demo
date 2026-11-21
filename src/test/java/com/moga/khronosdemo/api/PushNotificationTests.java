package com.moga.khronosdemo.api;

import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.entities.Zone;
import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Devices that get push notifications: who can register one, which endpoints are accepted, when they
 * are forgotten, and one push per device for each notification. Pushes go out after commit on another
 * thread, hence the timeouts; WebPushClient is mocked, so nothing leaves the test.
 */
class PushNotificationTests extends IntegrationTest {

    private static final String PHONE = "https://fcm.googleapis.com/fcm/send/phone-1";
    private static final String LAPTOP = "https://web.push.apple.com/laptop-1";
    private static final String P256DH = "BBQ8TGzcOPkE6Ue6b3Q_VQT7HcAPxQcj7lxz1YkcbySWOCYosmLLm5BtBfV0kQ-KvvxApXr2JFCbdygZ_RRO1-E";
    private static final String AUTH = "1MFE0QgKg3vX4AJBXQjXYA";
    private static final long WAIT_MS = 5000;
    private static final long QUIET_MS = 1000;

    private User owner;
    private Zone hall;
    private Worker worker;
    private Worker colleague;

    @BeforeEach
    void setUp() {
        owner = createOwner("owner");
        Establishment venue = createEstablishment(owner);
        hall = createZone(venue, "Hall");
        worker = createWorker(venue, "worker");
        colleague = createWorker(venue, "colleague");
        when(webPushClient.send(anyString(), anyString(), anyString(), any())).thenReturn(201);
    }

    private static String subscription(String endpoint) {
        return """
                { "endpoint": "%s", "expirationTime": null, "keys": { "p256dh": "%s", "auth": "%s" } }
                """.formatted(endpoint, P256DH, AUTH);
    }

    private void subscribe(Worker who, String endpoint) throws Exception {
        mockMvc.perform(putJson("/api/push-subscriptions", subscription(endpoint)).with(as(who)))
                .andExpect(status().isNoContent());
    }

    private List<String> devicesOf(Worker who) {
        return jdbcTemplate.queryForList("select endpoint from push_subscriptions where user_id = ? order by endpoint",
                String.class, who.getUserId());
    }

    private void assignShift(Worker who) throws Exception {
        mockMvc.perform(postJson("/api/worker-shifts", """
                        [ { "workerId": %d, "zoneId": %d, "date": "2030-05-10", "startTime": "18:00", "endTime": "02:00" } ]
                        """.formatted(who.getUserId(), hall.getId())).with(as(owner)))
                .andExpect(status().isCreated());
    }

    @Test
    void anySignedInUserCanReadThePublicKey() throws Exception {
        mockMvc.perform(getJson("/api/push-subscriptions/public-key").with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKey").value("BFfioXriDLXRtXOUjFkHaVKNk-2Z0qX_IvsJzFF8HoyfMH8jeyl1R_GuCEapxHukXU61-AZt_OOSEPg-i5257Gw"));
        mockMvc.perform(getJson("/api/push-subscriptions/public-key")).andExpect(status().isUnauthorized());
    }

    @Test
    void aWorkerRegistersEachDeviceOnce() throws Exception {
        subscribe(worker, PHONE);
        subscribe(worker, PHONE);
        subscribe(worker, LAPTOP);

        assertEquals(List.of(PHONE, LAPTOP), devicesOf(worker));
    }

    @Test
    void aDeviceMovesToWhoeverTurnsNotificationsOnThere() throws Exception {
        subscribe(worker, PHONE);
        subscribe(colleague, PHONE);

        assertEquals(List.of(), devicesOf(worker));
        assertEquals(List.of(PHONE), devicesOf(colleague));
    }

    @Test
    void onlyWorkersCanTurnNotificationsOn() throws Exception {
        mockMvc.perform(putJson("/api/push-subscriptions", subscription(PHONE)).with(as(owner)))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyHttpsEndpointsOfKnownPushServicesAreAccepted() throws Exception {
        for (String endpoint : List.of("http://fcm.googleapis.com/fcm/send/x", "https://evil.example.test/x",
                "https://fcm.googleapis.com.evil.example.test/x", "https://localhost:8080/api/users",
                "https://169.254.169.254/latest", "not a url")) {
            mockMvc.perform(putJson("/api/push-subscriptions", subscription(endpoint)).with(as(worker)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.endpoint").exists());
        }
        assertEquals(List.of(), devicesOf(worker));
    }

    @Test
    void keysThatAreNotAP256KeyAndA16ByteSecretAreRefused() throws Exception {
        mockMvc.perform(putJson("/api/push-subscriptions", """
                        { "endpoint": "%s", "keys": { "p256dh": "%s", "auth": "c2hvcnQ" } }
                        """.formatted(PHONE, P256DH)).with(as(worker)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(putJson("/api/push-subscriptions", """
                        { "endpoint": "%s", "keys": { "p256dh": "AAAA", "auth": "%s" } }
                        """.formatted(PHONE, AUTH)).with(as(worker)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(putJson("/api/push-subscriptions", """
                        { "endpoint": "%s" }
                        """.formatted(PHONE)).with(as(worker)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aUserCanOnlyRemoveTheirOwnDevice() throws Exception {
        subscribe(worker, PHONE);

        mockMvc.perform(deleteJson("/api/push-subscriptions?endpoint={e}", PHONE).with(as(colleague)))
                .andExpect(status().isNoContent());
        assertEquals(List.of(PHONE), devicesOf(worker));

        mockMvc.perform(deleteJson("/api/push-subscriptions?endpoint={e}", PHONE).with(as(worker)))
                .andExpect(status().isNoContent());
        assertEquals(List.of(), devicesOf(worker));
    }

    @Test
    void changingOrResettingThePasswordForgetsEveryDevice() throws Exception {
        subscribe(worker, PHONE);
        subscribe(worker, LAPTOP);
        mockMvc.perform(putJson("/api/users/{id}/password", """
                        { "currentPassword": "%s", "newPassword": "a-new-password-42" }
                        """.formatted(PASSWORD), worker.getUserId()).with(as(worker)))
                .andExpect(status().is2xxSuccessful());
        assertEquals(List.of(), devicesOf(worker));

        subscribe(colleague, PHONE);
        mockMvc.perform(putJson("/api/users/{id}/password/reset", """
                        { "newPassword": "a-new-password-42" }
                        """, colleague.getUserId()).with(as(owner)))
                .andExpect(status().is2xxSuccessful());
        assertEquals(List.of(), devicesOf(colleague));
    }

    @Test
    void deactivatingAWorkerForgetsTheirDevices() throws Exception {
        subscribe(worker, PHONE);

        mockMvc.perform(putJson("/api/workers/{id}/active", """
                        { "active": false }
                        """, worker.getUserId()).with(as(owner)))
                .andExpect(status().isOk());

        assertEquals(List.of(), devicesOf(worker));
    }

    @Test
    void anAssignmentReachesEachOfTheWorkersDevices() throws Exception {
        subscribe(worker, PHONE);
        subscribe(worker, LAPTOP);
        subscribe(colleague, "https://fcm.googleapis.com/fcm/send/colleague");

        assignShift(worker);

        ArgumentCaptor<String> endpoints = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> payloads = ArgumentCaptor.forClass(byte[].class);
        verify(webPushClient, timeout(WAIT_MS).times(2)).send(endpoints.capture(), eq(P256DH), eq(AUTH), payloads.capture());
        verify(webPushClient, after(QUIET_MS).times(2)).send(anyString(), anyString(), anyString(), any());
        assertEquals(List.of(PHONE, LAPTOP), endpoints.getAllValues().stream().sorted().toList());
        String payload = new String(payloads.getValue());
        assertTrue(payload.contains("\"title\":\"New shift on 10/05/2030\""), payload);
        assertTrue(payload.contains("\"body\":\"10/05/2030 · 18:00 - 02:00 (+1) · Hall\""), payload);
        assertTrue(payload.contains("\"url\":\"/notifications\""), payload);
    }

    @Test
    void aRequestThatFailsSendsNoPush() throws Exception {
        subscribe(worker, PHONE);

        mockMvc.perform(postJson("/api/worker-shifts", """
                        [
                          { "workerId": %1$d, "zoneId": %2$d, "date": "2030-05-10", "startTime": "18:00", "endTime": "23:00" },
                          { "workerId": %1$d, "zoneId": %2$d, "date": "2030-05-10", "startTime": "20:00", "endTime": "22:00" }
                        ]
                        """.formatted(worker.getUserId(), hall.getId())).with(as(owner)))
                .andExpect(status().is4xxClientError());

        verify(webPushClient, after(QUIET_MS).never()).send(anyString(), anyString(), anyString(), any());
    }

    @Test
    void aDeviceThePushServiceSaysIsGoneIsForgotten() throws Exception {
        subscribe(worker, PHONE);
        subscribe(worker, LAPTOP);
        when(webPushClient.send(eq(PHONE), anyString(), anyString(), any())).thenReturn(410);

        assignShift(worker);

        verify(webPushClient, timeout(WAIT_MS).times(2)).send(anyString(), anyString(), anyString(), any());
        long deadline = System.currentTimeMillis() + WAIT_MS;
        while (devicesOf(worker).size() > 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertEquals(List.of(LAPTOP), devicesOf(worker));
    }
}
