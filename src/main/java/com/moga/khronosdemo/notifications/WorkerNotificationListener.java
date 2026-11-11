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
    public void onShiftAssigned(ShiftAssignedNotification notification) {
        send(notification.recipient(),
                "New shift on " + notification.shift().date().format(DATE),
                "<p>You have been assigned a new shift:</p>" + shiftList(notification.shift()));
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
    public void onEventAssigned(EventAssignedNotification notification) {
        send(notification.recipient(),
                "You have been assigned to " + notification.event().title(),
                "<p>You have been assigned to an event:</p>" + eventList(notification.event()));
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

    private String shiftList(ShiftDetails shift) {
        return """
                <ul>
                  <li><strong>Date:</strong> %s</li>
                  <li><strong>Time:</strong> %s - %s</li>
                  <li><strong>Zone:</strong> %s</li>
                </ul>
                """.formatted(
                shift.date().format(DATE),
                shift.startTime().format(TIME),
                shift.endTime().format(TIME),
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
                htmlEscape(event.description()));
    }

    private void send(Recipient recipient, String subject, String body) {
        String html = "<p>Hi " + htmlEscape(recipient.firstName()) + ",</p>" + body;
        try {
            emailService.send(new EmailRequestDTO(recipient.email(), subject, html));
        } catch (EmailDeliveryException ex) {
            log.debug("Notification to {} not delivered; recorded in email_logs", recipient.email());
        }
    }
}
