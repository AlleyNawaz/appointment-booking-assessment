package com.clinic.booking.notification;

import com.clinic.booking.booking.domain.Appointment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * §19 #41: sends the booking confirmation/pending email as an async,
 * best-effort side effect — never a transaction participant, and a delivery
 * failure never rolls back or otherwise affects the booking itself, which is
 * why this is only ever called *after* the booking transaction commits.
 * Logged on failure, not rethrown.
 */
import com.clinic.booking.booking.domain.AppointmentType;
import com.clinic.booking.booking.domain.Provider;
import com.clinic.booking.booking.repository.AppointmentTypeRepository;
import com.clinic.booking.booking.repository.ProviderRepository;
import com.clinic.booking.config.BookingProperties;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.JavaMailSender;

@Service
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private final JavaMailSender mailSender;
    private final ProviderRepository providerRepository;
    private final AppointmentTypeRepository appointmentTypeRepository;
    private final BookingProperties bookingProperties;

    /** The authenticated SMTP account (already configured for sending) — never a separate hardcoded address. */
    @Value("${spring.mail.username}")
    private String fromEmail;

    public EmailNotificationService(
            JavaMailSender mailSender,
            ProviderRepository providerRepository,
            AppointmentTypeRepository appointmentTypeRepository,
            BookingProperties bookingProperties) {
        this.mailSender = mailSender;
        this.providerRepository = providerRepository;
        this.appointmentTypeRepository = appointmentTypeRepository;
        this.bookingProperties = bookingProperties;
    }

    /** §15.5/§15.6: BCC is opt-in only (blank by default) — patient contact details are in the email body. */
    private String bccEmailOrNull() {
        String configured = bookingProperties.getNotificationBccEmail();
        return StringUtils.hasText(configured) ? configured : null;
    }

    @Async
    public void sendBookingNotification(Appointment appointment) {
        try {
            String bcc = bccEmailOrNull();
            log.info("Sending {} notification HTML email to {} for appointment {}{}",
                    appointment.getStatus(), appointment.getPatientEmail(), appointment.getConfirmationToken(),
                    bcc != null ? " (BCC configured)" : "");

            Provider provider = providerRepository.findById(appointment.getProviderId()).orElse(null);
            AppointmentType type = appointmentTypeRepository.findById(appointment.getAppointmentTypeId()).orElse(null);

            String providerName = provider != null ? provider.getFirstName() + " " + provider.getLastName() : "Unknown Provider";
            String appointmentTypeName = type != null ? type.getDisplayName() : "Unknown Type";

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(appointment.getPatientEmail());
            if (bcc != null) {
                helper.setBcc(bcc);
            }
            helper.setSubject("Appointment " + appointment.getStatus() + " - Riverside Family Clinic");

            String htmlContent = "<html>" +
                "<body style='font-family: Arial, sans-serif; color: #333; line-height: 1.6; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 8px;'>" +
                "<h2 style='color: #4A90E2; border-bottom: 2px solid #4A90E2; padding-bottom: 10px;'>Appointment " + appointment.getStatus() + "</h2>" +
                "<p>Hello <strong>" + appointment.getPatientFullName() + "</strong>,</p>" +
                "<p>Your appointment has been successfully " + appointment.getStatus().toString().toLowerCase() + ".</p>" +
                "<div style='background-color: #f9f9f9; padding: 15px; border-radius: 5px; margin: 20px 0;'>" +
                "  <h3 style='margin-top: 0; color: #555;'>Appointment Details:</h3>" +
                "  <table style='width: 100%; border-collapse: collapse;'>" +
                "    <tr><td style='padding: 5px 0;'><strong>Appointment ID:</strong></td><td>" + appointment.getId() + "</td></tr>" +
                "    <tr><td style='padding: 5px 0;'><strong>Patient Name:</strong></td><td>" + appointment.getPatientFullName() + "</td></tr>" +
                "    <tr><td style='padding: 5px 0;'><strong>Patient Email:</strong></td><td>" + appointment.getPatientEmail() + "</td></tr>" +
                "    <tr><td style='padding: 5px 0;'><strong>Patient Phone:</strong></td><td>" + appointment.getPatientPhone() + "</td></tr>" +
                "    <tr><td style='padding: 5px 0;'><strong>Appointment Type:</strong></td><td>" + appointmentTypeName + "</td></tr>" +
                "    <tr><td style='padding: 5px 0;'><strong>Provider:</strong></td><td>" + providerName + "</td></tr>" +
                "    <tr><td style='padding: 5px 0;'><strong>Start Time:</strong></td><td>" + appointment.getStartDatetime() + "</td></tr>" +
                "    <tr><td style='padding: 5px 0;'><strong>Booking Timestamp:</strong></td><td>" + appointment.getCreatedAt() + "</td></tr>" +
                "    <tr><td style='padding: 5px 0;'><strong>Confirmation Token:</strong></td><td><code style='background: #e8e8e8; padding: 2px 5px; border-radius: 3px;'>" + appointment.getConfirmationToken() + "</code></td></tr>" +
                "  </table>" +
                "</div>" +
                "<p style='color: #777; font-size: 12px; margin-top: 30px; text-align: center;'>Thank you for choosing Riverside Family Clinic.</p>" +
                "</body>" +
                "</html>";

            helper.setText(htmlContent, true); // true indicates it is HTML

            mailSender.send(message);
            log.info("Email sent successfully!");
        } catch (Exception e) {
            log.error("Failed to send {} notification email for appointment {}",
                    appointment.getStatus(), appointment.getConfirmationToken(), e);
        }
    }

    @Async
    public void sendCancelNotification(Appointment appointment) {
        try {
            String bcc = bccEmailOrNull();
            log.info("Sending CANCELLED notification HTML email to {} for appointment {}{}",
                    appointment.getPatientEmail(), appointment.getConfirmationToken(),
                    bcc != null ? " (BCC configured)" : "");

            Provider provider = providerRepository.findById(appointment.getProviderId()).orElse(null);
            AppointmentType type = appointmentTypeRepository.findById(appointment.getAppointmentTypeId()).orElse(null);

            String providerName = provider != null ? provider.getFirstName() + " " + provider.getLastName() : "Unknown Provider";
            String appointmentTypeName = type != null ? type.getDisplayName() : "Unknown Type";

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(appointment.getPatientEmail());
            if (bcc != null) {
                helper.setBcc(bcc);
            }
            helper.setSubject("Appointment Cancelled - Riverside Family Clinic");

            String htmlContent = "<html>" +
                "<body style='font-family: Arial, sans-serif; color: #333; line-height: 1.6; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 8px;'>" +
                "<h2 style='color: #E24A4A; border-bottom: 2px solid #E24A4A; padding-bottom: 10px;'>Appointment Cancelled</h2>" +
                "<p>Hello <strong>" + appointment.getPatientFullName() + "</strong>,</p>" +
                "<p>Your appointment has been successfully cancelled as requested.</p>" +
                "<div style='background-color: #f9f9f9; padding: 15px; border-radius: 5px; margin: 20px 0;'>" +
                "  <h3 style='margin-top: 0; color: #555;'>Cancelled Appointment Details:</h3>" +
                "  <table style='width: 100%; border-collapse: collapse;'>" +
                "    <tr><td style='padding: 5px 0;'><strong>Appointment Type:</strong></td><td>" + appointmentTypeName + "</td></tr>" +
                "    <tr><td style='padding: 5px 0;'><strong>Provider:</strong></td><td>" + providerName + "</td></tr>" +
                "    <tr><td style='padding: 5px 0;'><strong>Start Time:</strong></td><td>" + appointment.getStartDatetime() + "</td></tr>" +
                "  </table>" +
                "</div>" +
                "<p>If you cancelled by mistake, you can always book a new appointment on our portal.</p>" +
                "<p style='color: #777; font-size: 12px; margin-top: 30px; text-align: center;'>Thank you for choosing Riverside Family Clinic.</p>" +
                "</body>" +
                "</html>";

            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Cancellation email sent successfully!");
        } catch (Exception e) {
            log.error("Failed to send CANCELLED notification email for appointment {}",
                    appointment.getConfirmationToken(), e);
        }
    }

    @Async
    public void sendRescheduleNotification(Appointment appointment, String oldConfirmationToken) {
        try {
            String bcc = bccEmailOrNull();
            log.info("Sending RESCHEDULED notification HTML email to {} for appointment {}{}",
                    appointment.getPatientEmail(), appointment.getConfirmationToken(),
                    bcc != null ? " (BCC configured)" : "");

            Provider provider = providerRepository.findById(appointment.getProviderId()).orElse(null);
            AppointmentType type = appointmentTypeRepository.findById(appointment.getAppointmentTypeId()).orElse(null);

            String providerName = provider != null ? provider.getFirstName() + " " + provider.getLastName() : "Unknown Provider";
            String appointmentTypeName = type != null ? type.getDisplayName() : "Unknown Type";

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(appointment.getPatientEmail());
            if (bcc != null) {
                helper.setBcc(bcc);
            }
            helper.setSubject("Appointment Rescheduled - Riverside Family Clinic");

            String htmlContent = "<html>" +
                "<body style='font-family: Arial, sans-serif; color: #333; line-height: 1.6; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 8px;'>" +
                "<h2 style='color: #4CAF50; border-bottom: 2px solid #4CAF50; padding-bottom: 10px;'>Appointment Rescheduled</h2>" +
                "<p>Hello <strong>" + appointment.getPatientFullName() + "</strong>,</p>" +
                "<p>Your appointment has been successfully rescheduled.</p>" +
                "<div style='background-color: #f9f9f9; padding: 15px; border-radius: 5px; margin: 20px 0;'>" +
                "  <h3 style='margin-top: 0; color: #555;'>New Appointment Details:</h3>" +
                "  <table style='width: 100%; border-collapse: collapse;'>" +
                "    <tr><td style='padding: 5px 0;'><strong>Appointment Type:</strong></td><td>" + appointmentTypeName + "</td></tr>" +
                "    <tr><td style='padding: 5px 0;'><strong>Provider:</strong></td><td>" + providerName + "</td></tr>" +
                "    <tr><td style='padding: 5px 0;'><strong>New Start Time:</strong></td><td>" + appointment.getStartDatetime() + "</td></tr>" +
                "    <tr><td style='padding: 5px 0;'><strong>New Confirmation Token:</strong></td><td><code style='background: #e8e8e8; padding: 2px 5px; border-radius: 3px;'>" + appointment.getConfirmationToken() + "</code></td></tr>" +
                "  </table>" +
                "</div>" +
                "<p>Your previous token (" + oldConfirmationToken + ") is no longer valid. Please use the new token above to manage your appointment.</p>" +
                "<p style='color: #777; font-size: 12px; margin-top: 30px; text-align: center;'>Thank you for choosing Riverside Family Clinic.</p>" +
                "</body>" +
                "</html>";

            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Reschedule email sent successfully!");
        } catch (Exception e) {
            log.error("Failed to send RESCHEDULE notification email for appointment {}",
                    appointment.getConfirmationToken(), e);
        }
    }
}
