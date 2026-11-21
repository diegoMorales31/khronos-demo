package com.moga.khronosdemo.notifications;

import com.moga.khronosdemo.services.PushService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationDeliveryTests {

    private static final Recipient LUCIA = new Recipient(7L);

    private static ShiftDetails shift(int day, int startHour, int endHour, String zone) {
        return new ShiftDetails(LocalDate.of(2030, 5, day), LocalTime.of(startHour, 0), LocalTime.of(endHour, 0), zone);
    }

    private static EventDetails event(String title, int day, int hour, String description) {
        return new EventDetails(title, LocalDateTime.of(2030, 5, day, hour, 0), "Hall", description);
    }

    private static PushMessage onlyPush(PushService pushService) {
        ArgumentCaptor<PushMessage> push = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushService).send(eq(7L), push.capture());
        return push.getValue();
    }

    @Test
    void anEventIsPushedWithItsDateTitleAndZone() {
        PushService pushService = mock(PushService.class);
        new WorkerNotificationListener(pushService).onAssigned(
                new AssignmentsNotification(LUCIA, List.of(), List.of(event("Wedding dinner", 10, 19, null))));

        PushMessage push = onlyPush(pushService);
        assertEquals("You have been assigned to Wedding dinner", push.title());
        assertEquals("10/05/2030 19:00 · Wedding dinner · Hall", push.body());
        assertEquals("/notifications", push.url());
    }

    @Test
    void aShiftThatEndsAfterMidnightShowsItsEndOnTheNextDay() {
        PushService pushService = mock(PushService.class);
        new WorkerNotificationListener(pushService).onAssigned(
                new AssignmentsNotification(LUCIA, List.of(shift(10, 18, 2, "Bar")), List.of()));

        PushMessage push = onlyPush(pushService);
        assertEquals("New shift on 10/05/2030", push.title());
        assertEquals("10/05/2030 · 18:00 - 02:00 (+1) · Bar", push.body());
    }

    @Test
    void shiftsComeInTheOrderTheyStart() {
        PushService pushService = mock(PushService.class);
        new WorkerNotificationListener(pushService).onAssigned(new AssignmentsNotification(LUCIA,
                List.of(shift(11, 10, 14, "Terrace"), shift(10, 18, 2, "Bar")), List.of()));

        PushMessage push = onlyPush(pushService);
        assertEquals("You have 2 new shifts", push.title());
        assertEquals("10/05/2030 · 18:00 - 02:00 (+1) · Bar\n11/05/2030 · 10:00 - 14:00 · Terrace", push.body());
    }

    @Test
    void shiftsAndEventsAssignedTogetherShareOnePushShiftsFirst() {
        PushService pushService = mock(PushService.class);
        new WorkerNotificationListener(pushService).onAssigned(new AssignmentsNotification(LUCIA,
                List.of(shift(10, 18, 23, "Hall")),
                List.of(event("Cocktails", 10, 22, null), event("Wedding dinner", 10, 19, null))));

        PushMessage push = onlyPush(pushService);
        assertEquals("You have 1 new shift and 2 events", push.title());
        assertEquals("10/05/2030 · 18:00 - 23:00 · Hall\n10/05/2030 19:00 · Wedding dinner · Hall\n10/05/2030 22:00 · Cocktails · Hall",
                push.body());
    }

    @Test
    void aLongListIsCutToAFewLines() {
        PushService pushService = mock(PushService.class);
        new WorkerNotificationListener(pushService).onAssigned(new AssignmentsNotification(LUCIA,
                List.of(shift(10, 9, 13, "Bar"), shift(11, 9, 13, "Bar"), shift(12, 9, 13, "Bar"), shift(13, 9, 13, "Bar")),
                List.of()));

        assertEquals("10/05/2030 · 09:00 - 13:00 · Bar\n11/05/2030 · 09:00 - 13:00 · Bar\nand 2 more", onlyPush(pushService).body());
    }

    @Test
    void anUnassignmentCarriesItsReason() {
        PushService pushService = mock(PushService.class);
        new WorkerNotificationListener(pushService).onEventUnassigned(
                new EventUnassignedNotification(LUCIA, event("Wedding dinner", 10, 19, null), "The shift no longer covers it"));

        PushMessage push = onlyPush(pushService);
        assertEquals("You are no longer assigned to Wedding dinner", push.title());
        assertEquals("10/05/2030 19:00 · Wedding dinner · Hall\nThe shift no longer covers it", push.body());
    }

    @Test
    void aFailedPushIsOnlyLogged() {
        PushService pushService = mock(PushService.class);
        doThrow(new IllegalStateException("push service down")).when(pushService).send(any(), any());

        new WorkerNotificationListener(pushService).onShiftCancelled(new ShiftCancelledNotification(LUCIA, shift(10, 18, 23, "Bar")));

        verify(pushService).send(eq(7L), any());
    }
}
