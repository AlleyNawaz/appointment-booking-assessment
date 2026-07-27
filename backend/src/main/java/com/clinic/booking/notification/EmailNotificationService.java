package com.clinic.booking.notification;

import com.clinic.booking.booking.domain.Appointment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * §19 #41: sends the booking confirmation/pending email as an async,
 * best-effort side effect — never a transaction participant, and a delivery
 * failure never rolls back or otherwise affects the booking itself, which is
 * why this is only ever called *after* the booking transaction commits.
 * Logged on failure, not rethrown.
 */
@Service
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    @Async
    public void sendBookingNotification(Appointment appointment) {
        try {
            log.info("Sending {} notification email to {} for appointment {}",
                    appointment.getStatus(), appointment.getPatientEmail(), appointment.getConfirmationToken());
            // Actual email delivery (SMTP/provider integration) is out of scope for this
            // milestone; this is the async, best-effort side-effect boundary §19 #41 requires.
        } catch (Exception e) {
            log.error("Failed to send {} notification email for appointment {}",
                    appointment.getStatus(), appointment.getConfirmationToken(), e);
        }
    }
}
