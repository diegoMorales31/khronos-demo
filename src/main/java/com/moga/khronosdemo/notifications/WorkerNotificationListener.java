package com.moga.khronosdemo.notifications;

import com.moga.khronosdemo.services.PushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Pushes each notification to the worker's devices, after commit and on another thread, so a slow push
 * service never holds up the request. The notice itself is already in the in-app list
 * (InboxNotificationWriter), which a tap on the push opens.
 */
@Component
public class WorkerNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(WorkerNotificationListener.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    // A push shows a few lines; the rest are summed up as "and N more"
    private static final int PUSH_LINES = 3;

    private final PushService pushService;

    public WorkerNotificationListener(PushService pushService) {
        this.pushService = pushService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAssigned(AssignmentsNotification notification) {
        List<ShiftDetails> shifts = notification.shifts().stream()
                .sorted(Comparator.comparing(ShiftDetails::startDateTime))
                .toList();
        List<EventDetails> events = notification.events().stream()
                .sorted(Comparator.comparing(EventDetails::dateTime))
                .toList();
        if (shifts.isEmpty() && events.isEmpty()) {
            return;
        }

        List<String> lines = Stream.concat(shifts.stream().map(this::shiftLine), events.stream().map(this::eventLine)).toList();
        push(notification.recipient(), assignedTitle(shifts, events), pushLines(lines));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onShiftChanged(ShiftChangedNotification notification) {
        push(notification.recipient(),
                "Your shift on " + notification.before().date().format(DATE) + " has changed",
                "Now: " + shiftLine(notification.after()));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onShiftCancelled(ShiftCancelledNotification notification) {
        push(notification.recipient(),
                "Your shift on " + notification.shift().date().format(DATE) + " has been cancelled",
                shiftLine(notification.shift()));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEventChanged(EventChangedNotification notification) {
        push(notification.recipient(),
                "The event " + notification.before().title() + " has changed",
                "Now: " + eventLine(notification.after()));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEventUnassigned(EventUnassignedNotification notification) {
        push(notification.recipient(),
                "You are no longer assigned to " + notification.event().title(),
                eventLine(notification.event()) + (notification.reason() != null ? "\n" + notification.reason() : ""));
    }

    private static String assignedTitle(List<ShiftDetails> shifts, List<EventDetails> events) {
        if (events.isEmpty()) {
            return shifts.size() == 1 ? "New shift on " + shifts.get(0).date().format(DATE) : "You have " + shifts.size() + " new shifts";
        }
        if (shifts.isEmpty()) {
            return events.size() == 1 ? "You have been assigned to " + events.get(0).title()
                    : "You have been assigned to " + events.size() + " events";
        }
        return "You have " + count(shifts.size(), "new shift") + " and " + count(events.size(), "event");
    }

    private static String count(int n, String noun) {
        return n + " " + noun + (n == 1 ? "" : "s");
    }

    // "10/05/2030 · 18:00 - 02:00 (+1) · Bar": a shift that ends after midnight shows its end as the next day
    private String shiftLine(ShiftDetails shift) {
        return "%s · %s - %s%s · %s".formatted(
                shift.date().format(DATE),
                shift.startTime().format(TIME),
                shift.endTime().format(TIME),
                shift.endsNextDay() ? " (+1)" : "",
                shift.zoneName());
    }

    // "10/05/2030 19:00 · Wedding dinner · Hall"
    private String eventLine(EventDetails event) {
        return "%s %s · %s · %s".formatted(
                event.dateTime().format(DATE), event.dateTime().format(TIME), event.title(), event.zoneName());
    }

    private static String pushLines(List<String> lines) {
        if (lines.size() <= PUSH_LINES) {
            return String.join("\n", lines);
        }
        return String.join("\n", lines.subList(0, PUSH_LINES - 1)) + "\nand " + (lines.size() - PUSH_LINES + 1) + " more";
    }

    private void push(Recipient recipient, String title, String body) {
        try {
            pushService.send(recipient.userId(), PushMessage.toNotifications(title, body));
        } catch (RuntimeException ex) {
            log.warn("Push notification to user {} not sent: {}", recipient.userId(), ex.getMessage());
        }
    }
}
