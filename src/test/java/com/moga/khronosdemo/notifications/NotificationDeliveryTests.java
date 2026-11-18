package com.moga.khronosdemo.notifications;

import com.moga.khronosdemo.dtos.request.EmailRequestDTO;
import com.moga.khronosdemo.entities.EmailLog;
import com.moga.khronosdemo.entities.enums.EmailStatus;
import com.moga.khronosdemo.exceptions.EmailDeliveryException;
import com.moga.khronosdemo.mappers.EmailLogMapper;
import com.moga.khronosdemo.repositories.EmailLogRepository;
import com.moga.khronosdemo.services.EmailService;
import com.moga.khronosdemo.services.impl.EmailServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.ServerSocket;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationDeliveryTests {

    @Test
    void anEventWithoutADescriptionIsStillEmailed() {
        EmailService emailService = mock(EmailService.class);
        WorkerNotificationListener listener = new WorkerNotificationListener(emailService);

        listener.onEventAssigned(new EventAssignedNotification(new Recipient("lucia@example.test", "Lucia"),
                new EventDetails("Wedding dinner", LocalDateTime.of(2030, 5, 10, 19, 0), "Hall", null)));

        ArgumentCaptor<EmailRequestDTO> email = ArgumentCaptor.forClass(EmailRequestDTO.class);
        verify(emailService).send(email.capture());
        assertEquals("lucia@example.test", email.getValue().to());
        assertTrue(email.getValue().html().contains("<strong>Description:</strong> -"));
    }

    @Test
    void aShiftThatEndsAfterMidnightShowsItsEndOnTheNextDay() {
        EmailService emailService = mock(EmailService.class);
        WorkerNotificationListener listener = new WorkerNotificationListener(emailService);
        Recipient lucia = new Recipient("lucia@example.test", "Lucia");

        listener.onShiftAssigned(new ShiftAssignedNotification(lucia,
                new ShiftDetails(LocalDate.of(2030, 5, 10), LocalTime.of(18, 0), LocalTime.of(2, 0), "Bar")));
        listener.onShiftAssigned(new ShiftAssignedNotification(lucia,
                new ShiftDetails(LocalDate.of(2030, 5, 11), LocalTime.of(10, 0), LocalTime.of(14, 0), "Bar")));

        ArgumentCaptor<EmailRequestDTO> email = ArgumentCaptor.forClass(EmailRequestDTO.class);
        verify(emailService, times(2)).send(email.capture());
        assertTrue(email.getAllValues().get(0).html().contains("<strong>Time:</strong> 18:00 - 02:00 (+1)</li>"));
        assertTrue(email.getAllValues().get(0).html().contains("<strong>Date:</strong> 10/05/2030"));
        assertTrue(email.getAllValues().get(1).html().contains("<strong>Time:</strong> 10:00 - 14:00</li>"));
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
