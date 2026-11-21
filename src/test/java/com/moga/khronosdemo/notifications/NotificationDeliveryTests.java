package com.moga.khronosdemo.notifications;

import com.moga.khronosdemo.dtos.request.EmailRequestDTO;
import com.moga.khronosdemo.entities.EmailLog;
import com.moga.khronosdemo.entities.enums.EmailStatus;
import com.moga.khronosdemo.exceptions.EmailDeliveryException;
import com.moga.khronosdemo.mappers.EmailLogMapper;
import com.moga.khronosdemo.repositories.EmailLogRepository;
import com.moga.khronosdemo.services.EmailService;
import com.moga.khronosdemo.services.PushService;
import com.moga.khronosdemo.services.impl.EmailServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.ServerSocket;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationDeliveryTests {

    private static final Recipient LUCIA = new Recipient(7L, "lucia@example.test", "Lucia");

    private static ShiftDetails shift(int day, int startHour, int endHour, String zone) {
        return new ShiftDetails(LocalDate.of(2030, 5, day), LocalTime.of(startHour, 0), LocalTime.of(endHour, 0), zone);
    }

    private static EventDetails event(String title, int day, int hour, String description) {
        return new EventDetails(title, LocalDateTime.of(2030, 5, day, hour, 0), "Hall", description);
    }

    private static EmailRequestDTO onlyEmail(EmailService emailService) {
        ArgumentCaptor<EmailRequestDTO> email = ArgumentCaptor.forClass(EmailRequestDTO.class);
        verify(emailService).send(email.capture());
        return email.getValue();
    }

    @Test
    void anEventWithoutADescriptionIsStillEmailed() {
        EmailService emailService = mock(EmailService.class);
        new WorkerNotificationListener(emailService, mock(PushService.class)).onAssigned(
                new AssignmentsNotification(LUCIA, List.of(), List.of(event("Wedding dinner", 10, 19, null))));

        EmailRequestDTO email = onlyEmail(emailService);
        assertEquals("lucia@example.test", email.to());
        assertEquals("You have been assigned to Wedding dinner", email.subject());
        assertTrue(email.html().contains("<li><strong>10/05/2030 19:00</strong> · Wedding dinner · Hall</li>"));
    }

    @Test
    void aShiftThatEndsAfterMidnightShowsItsEndOnTheNextDay() {
        EmailService emailService = mock(EmailService.class);
        new WorkerNotificationListener(emailService, mock(PushService.class)).onAssigned(
                new AssignmentsNotification(LUCIA, List.of(shift(10, 18, 2, "Bar")), List.of()));

        EmailRequestDTO email = onlyEmail(emailService);
        assertEquals("New shift on 10/05/2030", email.subject());
        assertTrue(email.html().contains("<li><strong>10/05/2030</strong> · 18:00 - 02:00 (+1) · Bar</li>"));
    }

    @Test
    void severalShiftsGoInOneListInTheOrderTheyStart() {
        EmailService emailService = mock(EmailService.class);
        new WorkerNotificationListener(emailService, mock(PushService.class)).onAssigned(new AssignmentsNotification(LUCIA,
                List.of(shift(11, 10, 14, "Terrace"), shift(10, 18, 2, "Bar")), List.of()));

        EmailRequestDTO email = onlyEmail(emailService);
        assertEquals("You have 2 new shifts", email.subject());
        assertTrue(email.html().indexOf("10/05/2030") < email.html().indexOf("11/05/2030"));
        assertTrue(email.html().contains("· 10:00 - 14:00 · Terrace</li>"));
    }

    @Test
    void severalEventsGoInOneListInDateOrder() {
        EmailService emailService = mock(EmailService.class);
        new WorkerNotificationListener(emailService, mock(PushService.class)).onAssigned(new AssignmentsNotification(LUCIA, List.of(),
                List.of(event("Cocktails", 10, 22, "Bar service"), event("Wedding dinner", 10, 19, null))));

        EmailRequestDTO email = onlyEmail(emailService);
        assertEquals("You have been assigned to 2 events", email.subject());
        assertTrue(email.html().indexOf("Wedding dinner") < email.html().indexOf("Cocktails"));
        assertTrue(email.html().contains("Cocktails · Hall<br>Bar service</li>"));
    }

    @Test
    void shiftsAndEventsAssignedTogetherShareOneEmail() {
        EmailService emailService = mock(EmailService.class);
        new WorkerNotificationListener(emailService, mock(PushService.class)).onAssigned(new AssignmentsNotification(LUCIA,
                List.of(shift(10, 18, 23, "Hall")),
                List.of(event("Cocktails", 10, 22, null), event("Wedding dinner", 10, 19, null))));

        EmailRequestDTO email = onlyEmail(emailService);
        assertEquals("You have 1 new shift and 2 events", email.subject());
        String html = email.html();
        assertTrue(html.indexOf("<strong>Shift</strong>") < html.indexOf("<strong>Events</strong>"));
        assertTrue(html.contains("<li><strong>10/05/2030</strong> · 18:00 - 23:00 · Hall</li>"));
        assertTrue(html.indexOf("Wedding dinner") < html.indexOf("Cocktails"));
    }

    @Test
    void aResendThatNeverAnswersTimesOutAndIsLoggedAsFailed() throws Exception {
        EmailLogRepository emailLogRepository = mock(EmailLogRepository.class);
        when(emailLogRepository.save(any(EmailLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Accepts the connection but never sends a response
        try (ServerSocket silentServer = new ServerSocket(0)) {
            EmailServiceImpl emailService = new EmailServiceImpl(emailLogRepository, new EmailLogMapper(), "test-key",
                    "Khronos <test@example.test>", "http://127.0.0.1:" + silentServer.getLocalPort(),
                    Duration.ofSeconds(1), Duration.ofMillis(300));

            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> assertThrows(EmailDeliveryException.class,
                    () -> emailService.send(new EmailRequestDTO("lucia@example.test", "Subject", "<p>Body</p>"))));
        }

        ArgumentCaptor<EmailLog> log = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(log.capture());
        assertEquals(EmailStatus.FAILED, log.getValue().getStatus());
        assertTrue(log.getValue().getErrorMessage().startsWith("Could not reach Resend"));
    }
}
