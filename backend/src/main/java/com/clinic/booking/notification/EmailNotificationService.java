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
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@Service
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);
    private static final String ADMIN_EMAIL = "AliSemanticWeb@gmail.com";

    private final JavaMailSender mailSender;

    public EmailNotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    public void sendBookingNotification(Appointment appointment) {
        try {
            log.info("Sending {} notification email to {} (and BCC to {}) for appointment {}",
                    appointment.getStatus(), appointment.getPatientEmail(), ADMIN_EMAIL, appointment.getConfirmationToken());
            
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(ADMIN_EMAIL); // You can change this to a generic no-reply if needed
            message.setTo(appointment.getPatientEmail());
            message.setBcc(ADMIN_EMAIL);
            message.setSubject("Appointment " + appointment.getStatus() + " - Riverside Family Clinic");
            message.setText("Hello " + appointment.getPatientFullName() + ",\n\n" +
                    "Your appointment for " + appointment.getStartDatetime() + " is currently " + appointment.getStatus() + ".\n\n" +
                    "Confirmation Token: " + appointment.getConfirmationToken() + "\n\n" +
                    "Thank you,\nRiverside Family Clinic");
            
            mailSender.send(message);
            log.info("Email sent successfully!");
        } catch (Exception e) {
            log.error("Failed to send {} notification email for appointment {}",
                    appointment.getStatus(), appointment.getConfirmationToken(), e);
        }
    }
}
