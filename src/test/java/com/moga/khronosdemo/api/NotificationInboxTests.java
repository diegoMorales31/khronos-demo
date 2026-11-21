package com.moga.khronosdemo.api;

import com.moga.khronosdemo.entities.Establishment;
import com.moga.khronosdemo.entities.User;
import com.moga.khronosdemo.entities.Worker;
import com.moga.khronosdemo.entities.WorkerShift;
import com.moga.khronosdemo.entities.Zone;
import com.moga.khronosdemo.entities.enums.NotificationType;
import com.moga.khronosdemo.services.NotificationService;
import com.moga.khronosdemo.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The in-app list: a notice per notification, saved with the change that caused it, that only its
 * owner sees and marks read.
 */
class NotificationInboxTests extends IntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2030, 5, 10);

    @Autowired
    private NotificationService notificationService;

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
    }

    private long noticesOf(Worker who) {
        return jdbcTemplate.queryForObject("select count(*) from notifications where user_id = ?", Long.class, who.getUserId());
    }

    @Test
    void anAssignmentLeavesOneNoticeWithItsShiftsInOrder() throws Exception {
        mockMvc.perform(postJson("/api/worker-shifts", """
                        [
                          { "workerId": %1$d, "zoneId": %2$d, "date": "2030-05-11", "startTime": "09:00", "endTime": "13:00" },
                          { "workerId": %1$d, "zoneId": %2$d, "date": "2030-05-10", "startTime": "18:00", "endTime": "02:00" }
                        ]
                        """.formatted(worker.getUserId(), hall.getId())).with(as(owner)))
                .andExpect(status().isCreated());

        mockMvc.perform(getJson("/api/notifications").with(as(worker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].type").value("ASSIGNED"))
                .andExpect(jsonPath("$.items[0].readAt").value(nullValue()))
                .andExpect(jsonPath("$.items[0].data.shifts", hasSize(2)))
                .andExpect(jsonPath("$.items[0].data.shifts[0].date").value("2030-05-10"))
                .andExpect(jsonPath("$.items[0].data.shifts[0].startTime").value("18:00"))
                .andExpect(jsonPath("$.items[0].data.shifts[0].endTime").value("02:00"))
                .andExpect(jsonPath("$.items[0].data.shifts[0].endsNextDay").value(true))
                .andExpect(jsonPath("$.items[0].data.shifts[0].zone").value("Hall"))
                .andExpect(jsonPath("$.items[0].data.events", hasSize(0)));
        assertEquals(0, noticesOf(colleague));
    }

    @Test
    void aRequestThatFailsLeavesNoNotice() throws Exception {
        mockMvc.perform(postJson("/api/worker-shifts", """
                        [
                          { "workerId": %1$d, "zoneId": %2$d, "date": "2030-05-10", "startTime": "18:00", "endTime": "23:00" },
                          { "workerId": %1$d, "zoneId": %2$d, "date": "2030-05-10", "startTime": "20:00", "endTime": "22:00" }
                        ]
                        """.formatted(worker.getUserId(), hall.getId())).with(as(owner)))
                .andExpect(status().is4xxClientError());

        assertEquals(0, noticesOf(worker));
    }

    @Test
    void editingAndDeletingAShiftLeaveTheirNotices() throws Exception {
        WorkerShift shift = createShift(worker, hall, DAY, "18:00", "23:00");

        mockMvc.perform(putJson("/api/worker-shifts/{id}", """
                        { "workerId": %d, "zoneId": %d, "date": "2030-05-10", "startTime": "19:00", "endTime": "23:00" }
                        """.formatted(worker.getUserId(), hall.getId()), shift.getId()).with(as(owner)))
                .andExpect(status().isOk());
        mockMvc.perform(deleteJson("/api/worker-shifts/{id}", shift.getId()).with(as(owner)))
                .andExpect(status().is2xxSuccessful());

        mockMvc.perform(getJson("/api/notifications").with(as(worker)))
                .andExpect(jsonPath("$.items[0].type").value("SHIFT_CANCELLED"))
                .andExpect(jsonPath("$.items[0].data.shift.startTime").value("19:00"))
                .andExpect(jsonPath("$.items[1].type").value("SHIFT_CHANGED"))
                .andExpect(jsonPath("$.items[1].data.before.startTime").value("18:00"))
                .andExpect(jsonPath("$.items[1].data.after.startTime").value("19:00"));
    }

    @Test
    void anEventDeletedSaysWhyTheWorkerWasTakenOff() throws Exception {
        createShift(worker, hall, DAY, "18:00", "23:00");
        var event = createEvent(hall, LocalDateTime.of(2030, 5, 10, 19, 0));
        assign(worker, event);

        mockMvc.perform(deleteJson("/api/events/{id}", event.getId()).with(as(owner)))
                .andExpect(status().is2xxSuccessful());

        mockMvc.perform(getJson("/api/notifications").with(as(worker)))
                .andExpect(jsonPath("$.items[0].type").value("EVENT_UNASSIGNED"))
                .andExpect(jsonPath("$.items[0].data.event.dateTime").value("2030-05-10T19:00"))
                .andExpect(jsonPath("$.items[0].data.event.zone").value("Hall"))
                .andExpect(jsonPath("$.items[0].data.reason").value("EVENT_CANCELLED"));
    }

    @Test
    void olderNoticesComeInPagesNewestFirst() throws Exception {
        for (int i = 1; i <= 5; i++) {
            notificationService.record(worker.getUserId(), NotificationType.SHIFT_CANCELLED, Map.of("n", i));
            clock.advance(Duration.ofMinutes(1));
        }

        String first = mockMvc.perform(getJson("/api/notifications?limit=2").with(as(worker)))
                .andExpect(jsonPath("$.items[0].data.n").value(5))
                .andExpect(jsonPath("$.items[1].data.n").value(4))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.unreadCount").value(5))
                .andReturn().getResponse().getContentAsString();
        Integer last = com.jayway.jsonpath.JsonPath.read(first, "$.items[1].id");

        mockMvc.perform(getJson("/api/notifications?limit=3&before={id}", last).with(as(worker)))
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[0].data.n").value(3))
                .andExpect(jsonPath("$.items[2].data.n").value(1))
                .andExpect(jsonPath("$.hasMore").value(false));

        mockMvc.perform(getJson("/api/notifications?limit=0").with(as(worker))).andExpect(status().isBadRequest());
        mockMvc.perform(getJson("/api/notifications?limit=101").with(as(worker))).andExpect(status().isBadRequest());
    }

    @Test
    void onlyTheOwnerReadsAndMarksANotice() throws Exception {
        notificationService.record(worker.getUserId(), NotificationType.SHIFT_CANCELLED, Map.of());
        Long id = jdbcTemplate.queryForObject("select id from notifications where user_id = ?", Long.class, worker.getUserId());

        mockMvc.perform(getJson("/api/notifications").with(as(colleague)))
                .andExpect(jsonPath("$.items", hasSize(0)));
        mockMvc.perform(postJson("/api/notifications/{id}/read", "", id).with(as(colleague)))
                .andExpect(status().isNotFound());
        mockMvc.perform(getJson("/api/notifications?before={id}", id).with(as(colleague)))
                .andExpect(status().isNotFound());

        mockMvc.perform(postJson("/api/notifications/{id}/read", "", id).with(as(worker)))
                .andExpect(status().isNoContent());
        mockMvc.perform(getJson("/api/notifications").with(as(worker)))
                .andExpect(jsonPath("$.unreadCount").value(0))
                .andExpect(jsonPath("$.items[0].readAt").value(notNullValue()));
    }

    @Test
    void everythingCanBeMarkedReadAtOnce() throws Exception {
        notificationService.record(worker.getUserId(), NotificationType.SHIFT_CANCELLED, Map.of());
        notificationService.record(worker.getUserId(), NotificationType.SHIFT_CANCELLED, Map.of());
        notificationService.record(colleague.getUserId(), NotificationType.SHIFT_CANCELLED, Map.of());

        mockMvc.perform(getJson("/api/notifications/unread-count").with(as(worker)))
                .andExpect(jsonPath("$.count").value(2));
        mockMvc.perform(postJson("/api/notifications/read-all", "").with(as(worker)))
                .andExpect(status().isNoContent());

        mockMvc.perform(getJson("/api/notifications/unread-count").with(as(worker)))
                .andExpect(jsonPath("$.count").value(0));
        mockMvc.perform(getJson("/api/notifications/unread-count").with(as(colleague)))
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void noticesOlderThanTheRetentionAreDeleted() {
        notificationService.record(worker.getUserId(), NotificationType.SHIFT_CANCELLED, Map.of());
        clock.advance(Duration.ofDays(60));
        notificationService.record(worker.getUserId(), NotificationType.SHIFT_CANCELLED, Map.of());
        clock.advance(Duration.ofDays(31));

        assertEquals(1, notificationService.deleteExpired());
        assertEquals(1, noticesOf(worker));
    }

    @Test
    void theListNeedsASignIn() throws Exception {
        mockMvc.perform(getJson("/api/notifications")).andExpect(status().isUnauthorized());
    }
}
