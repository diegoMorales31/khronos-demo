package com.moga.khronosdemo.notifications;

import com.moga.khronosdemo.dtos.request.EmailRequestDTO;
import com.moga.khronosdemo.exceptions.EmailDeliveryException;
import com.moga.khronosdemo.services.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.web.util.HtmlUtils.htmlEscape;

@Component
public class WorkerNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(WorkerNotificationListener.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final EmailService emailService;

    public WorkerNotificationListener(EmailService emailService) {
        this.emailService = emailService;
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

        StringBuilder body = new StringBuilder("<p>You have new work assigned:</p>");
        if (!shifts.isEmpty()) {
            body.append("<p><strong>").append(shifts.size() == 1 ? "Shift" : "Shifts").append("</strong></p>")
                    .append(assignedShiftList(shifts));
        }
        if (!events.isEmpty()) {
            body.append("<p><strong>").append(events.size() == 1 ? "Event" : "Events").append("</strong></p>")
                    .append(assignedEventList(events));
        }
        send(notification.recipient(), assignedSubject(shifts, events), body.toString());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onShiftChanged(ShiftChangedNotification notification) {
        send(notification.recipient(),
                "Your shift on " + notification.before().date().format(DATE) + " has changed",
                "<p>One of your shifts has been changed.</p>"
                        + "<p><strong>Before:</strong></p>" + shiftList(notification.before())
                        + "<p><strong>Now:</strong></p>" + shiftList(notification.after()));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onShiftCancelled(ShiftCancelledNotification notification) {
        send(notification.recipient(),
                "Your shift on " + notification.shift().date().format(DATE) + " has been cancelled",
                "<p>The following shift has been cancelled:</p>" + shiftList(notification.shift()));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEventChanged(EventChangedNotification notification) {
        send(notification.recipient(),
                "The event " + notification.before().title() + " has changed",
                "<p>An event you are assigned to has been changed.</p>"
                        + "<p><strong>Before:</strong></p>" + eventList(notification.before())
                        + "<p><strong>Now:</strong></p>" + eventList(notification.after()));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEventUnassigned(EventUnassignedNotification notification) {
        String reason = notification.reason() != null ? "<p>" + htmlEscape(notification.reason()) + "</p>" : "";
        send(notification.recipient(),
                "You are no longer assigned to " + notification.event().title(),
                "<p>You are no longer assigned to the following event:</p>" + eventList(notification.event()) + reason);
    }

    private static String assignedSubject(List<ShiftDetails> shifts, List<EventDetails> events) {
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

    // One line per shift: "10/05/2030 · 18:00 - 02:00 (+1) · Bar"
    private String assignedShiftList(List<ShiftDetails> shifts) {
        return shifts.stream()
                .map(shift -> "<li><strong>%s</strong> · %s - %s%s · %s</li>".formatted(
                        shift.date().format(DATE),
                        shift.startTime().format(TIME),
                        shift.endTime().format(TIME),
                        shift.endsNextDay() ? " (+1)" : "",
                        htmlEscape(shift.zoneName())))
                .collect(Collectors.joining("", "<ul>", "</ul>"));
    }

    // One line per event: "10/05/2030 19:00 · Wedding dinner · Hall", and its description below when it has one
    private String assignedEventList(List<EventDetails> events) {
        return events.stream()
                .map(event -> "<li><strong>%s %s</strong> · %s · %s%s</li>".formatted(
                        event.dateTime().format(DATE),
                        event.dateTime().format(TIME),
                        htmlEscape(event.title()),
                        htmlEscape(event.zoneName()),
                        event.description() != null ? "<br>" + htmlEscape(event.description()) : ""))
                .collect(Collectors.joining("", "<ul>", "</ul>"));
    }

    private String shiftList(ShiftDetails shift) {
        return """
                <ul>
                  <li><strong>Date:</strong> %s</li>
                  <li><strong>Time:</strong> %s - %s%s</li>
                  <li><strong>Zone:</strong> %s</li>
                </ul>
                """.formatted(
                shift.date().format(DATE),
                shift.startTime().format(TIME),
                shift.endTime().format(TIME),
                // A shift that ends after midnight shows its end as the next day: 18:00 - 02:00 (+1)
                shift.endsNextDay() ? " (+1)" : "",
                htmlEscape(shift.zoneName()));
    }

    private String eventList(EventDetails event) {
        return """
                <ul>
                  <li><strong>Event:</strong> %s</li>
                  <li><strong>Date:</strong> %s</li>
                  <li><strong>Starts at:</strong> %s</li>
                  <li><strong>Zone:</strong> %s</li>
                  <li><strong>Description:</strong> %s</li>
                </ul>
                """.formatted(
                htmlEscape(event.title()),
                event.dateTime().format(DATE),
                event.dateTime().format(TIME),
                htmlEscape(event.zoneName()),
                event.description() != null ? htmlEscape(event.description()) : "-");
    }

    private void send(Recipient recipient, String subject, String body) {
        String html = "<p>Hi " + htmlEscape(recipient.firstName()) + ",</p>" + body;
        try {
            emailService.send(new EmailRequestDTO(recipient.email(), subject, html));
        } catch (EmailDeliveryException ex) {
            log.warn("Notification to {} not delivered; recorded in email_logs", recipient.email());
        }
    }
}
