package com.moga.khronosdemo.notifications;

import com.moga.khronosdemo.entities.enums.NotificationType;
import com.moga.khronosdemo.services.NotificationService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Puts each notification in the worker's in-app list. Unlike the email and the push, which go after
 * commit, the notice is written just before the commit, in the same transaction: it is saved with the
 * change, or not at all. Only the facts are stored (NotificationType says the shape); the app words them
 * in the reader's language.
 */
@Component
public class InboxNotificationWriter {

    private final NotificationService notificationService;

    public InboxNotificationWriter(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onAssigned(AssignmentsNotification notification) {
        if (notification.shifts().isEmpty() && notification.events().isEmpty()) {
            return;
        }
        record(notification.recipient(), NotificationType.ASSIGNED, Map.of(
                "shifts", notification.shifts().stream().sorted(Comparator.comparing(ShiftDetails::startDateTime)).map(InboxNotificationWriter::shift).toList(),
                "events", notification.events().stream().sorted(Comparator.comparing(EventDetails::dateTime)).map(InboxNotificationWriter::event).toList()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onShiftChanged(ShiftChangedNotification notification) {
        record(notification.recipient(), NotificationType.SHIFT_CHANGED,
                Map.of("before", shift(notification.before()), "after", shift(notification.after())));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onShiftCancelled(ShiftCancelledNotification notification) {
        record(notification.recipient(), NotificationType.SHIFT_CANCELLED, Map.of("shift", shift(notification.shift())));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onEventChanged(EventChangedNotification notification) {
        record(notification.recipient(), NotificationType.EVENT_CHANGED,
                Map.of("before", event(notification.before()), "after", event(notification.after())));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onEventUnassigned(EventUnassignedNotification notification) {
        Map<String, Object> data = new HashMap<>();
        data.put("event", event(notification.event()));
        data.put("reason", reasonCode(notification.reason()));
        record(notification.recipient(), NotificationType.EVENT_UNASSIGNED, data);
    }

    private void record(Recipient recipient, NotificationType type, Object data) {
        notificationService.record(recipient.userId(), type, data);
    }

    // { date: "2030-05-10", startTime: "18:00", endTime: "02:00", endsNextDay: true, zone: "Bar" }
    private static Map<String, Object> shift(ShiftDetails shift) {
        return Map.of(
                "date", shift.date().toString(),
                "startTime", shift.startTime().format(DateTimeFormatter.ofPattern("HH:mm")),
                "endTime", shift.endTime().format(DateTimeFormatter.ofPattern("HH:mm")),
                "endsNextDay", shift.endsNextDay(),
                "zone", shift.zoneName());
    }

    // { title: "Wedding dinner", dateTime: "2030-05-10T19:00", zone: "Hall" }, in the establishment's local time
    private static Map<String, Object> event(EventDetails event) {
        return Map.of(
                "title", event.title(),
                "dateTime", event.dateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")),
                "zone", event.zoneName());
    }

    private static String reasonCode(String reason) {
        if (EventUnassignedNotification.EVENT_CANCELLED.equals(reason)) {
            return "EVENT_CANCELLED";
        }
        if (EventUnassignedNotification.NO_COVERING_SHIFT.equals(reason)) {
            return "NO_COVERING_SHIFT";
        }
        return null;
    }
}
