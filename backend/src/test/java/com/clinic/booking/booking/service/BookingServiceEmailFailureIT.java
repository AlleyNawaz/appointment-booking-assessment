package com.clinic.booking.booking.service;

import com.clinic.booking.booking.domain.Appointment;
import com.clinic.booking.booking.dto.AppointmentResponse;
import com.clinic.booking.booking.dto.CreateAppointmentRequest;
import com.clinic.booking.notification.EmailNotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.test.annotation.DirtiesContext;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves PRD §19 #41 / Milestone 5 validation checklist: an
 * {@link EmailNotificationService} failure never rolls back a successful
 * booking. {@code @TestConfiguration} swaps in a {@code @Primary} bean whose
 * override *re-declares* {@code @Async} — so it still goes through Spring's
 * real async proxy (the actual mechanism that isolates the caller from the
 * failure), rather than a plain Mockito stub, which would intercept the call
 * before that proxy ever runs and misrepresent the guarantee under test.
 * {@code @DirtiesContext} keeps this bean swap from leaking into any other
 * test class's cached context.
 */
@SpringBootTest
@Import(BookingServiceEmailFailureIT.FailingEmailNotificationConfig.class)
@DirtiesContext
class BookingServiceEmailFailureIT {

    private static final long GENERAL_CONSULT_TYPE_ID = 2L; // seeded by V2, §7.2
    private static final Calendar UTC_CALENDAR = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

    @Autowired
    private BookingService bookingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EmailNotificationService emailNotificationService;

    private long providerId;

    @BeforeEach
    void seedTestProviderWithAllDaysWorking() {
        String email = "booking-email-failure-it-" + UUID.randomUUID() + "@example.com";
        jdbcTemplate.update(
                "INSERT INTO providers (first_name, last_name, specialty, email, timezone, is_active) "
                        + "VALUES ('Test', 'Provider', 'General Medicine', ?, 'America/New_York', TRUE)",
                email);
        providerId = jdbcTemplate.queryForObject("SELECT id FROM providers WHERE email = ?", Long.class, email);
        for (int dayOfWeek = 0; dayOfWeek <= 6; dayOfWeek++) {
            jdbcTemplate.update(
                    "INSERT INTO provider_availability_rules (provider_id, day_of_week, start_time, end_time, rule_type) "
                            + "VALUES (?, ?, '00:00:00', '23:59:00', 'WORKING')",
                    providerId, dayOfWeek);
        }
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM appointments WHERE provider_id = ?", providerId);
        jdbcTemplate.update("DELETE FROM slot_holds WHERE provider_id = ?", providerId);
        jdbcTemplate.update("DELETE FROM provider_availability_rules WHERE provider_id = ?", providerId);
        jdbcTemplate.update("DELETE FROM providers WHERE id = ?", providerId);
    }

    @Test
    void emailNotificationFailure_doesNotRollBackTheBooking() {
        // Sanity-check the wiring itself: if this ever silently wired the real,
        // non-throwing service instead, the rest of the test would pass for the
        // wrong reason (never actually exercising the failure path).
        assertThat(emailNotificationService).isInstanceOf(FailingEmailNotificationService.class);

        Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
        String holdToken = insertHold(start);
        CreateAppointmentRequest request = new CreateAppointmentRequest(
                holdToken, "Jordan Rivera", "jordan@example.com", "+14155551234", "First visit");

        AppointmentResponse response = bookingService.createAppointment(request, UUID.randomUUID().toString());

        // The API/service call itself returned normally (no exception propagated from
        // the failing async email call), with the expected CONFIRMED status.
        assertThat(response.status()).isEqualTo(Appointment.Status.CONFIRMED);

        // The transaction committed: the appointment row is really persisted, with the
        // same status, and the consumed hold was deleted as part of the same commit —
        // none of this would be true had the email failure rolled the transaction back.
        String persistedStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM appointments WHERE confirmation_token = ?",
                String.class, response.confirmationToken());
        assertThat(persistedStatus).isEqualTo("CONFIRMED");

        Integer remainingHolds = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM slot_holds WHERE hold_token = ?", Integer.class, holdToken);
        assertThat(remainingHolds).isZero();
    }

    private String insertHold(Instant start) {
        String holdToken = UUID.randomUUID().toString();
        Instant end = start.plus(30, ChronoUnit.MINUTES);
        Instant expiresAt = Instant.now().plusSeconds(300);
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO slot_holds (provider_id, appointment_type_id, start_datetime, end_datetime, hold_token, expires_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?)");
            ps.setLong(1, providerId);
            ps.setLong(2, GENERAL_CONSULT_TYPE_ID);
            ps.setTimestamp(3, Timestamp.from(start), UTC_CALENDAR);
            ps.setTimestamp(4, Timestamp.from(end), UTC_CALENDAR);
            ps.setString(5, holdToken);
            ps.setTimestamp(6, Timestamp.from(expiresAt), UTC_CALENDAR);
            return ps;
        });
        return holdToken;
    }

    @TestConfiguration
    static class FailingEmailNotificationConfig {
        @Bean
        @Primary
        EmailNotificationService failingEmailNotificationService() {
            return new FailingEmailNotificationService();
        }
    }

    static class FailingEmailNotificationService extends EmailNotificationService {
        public FailingEmailNotificationService() {
            super(null, null, null);
        }

        @Override
        @Async
        public void sendBookingNotification(Appointment appointment) {
            throw new RuntimeException("Simulated email delivery failure — proves §19 #41 isolation");
        }
    }
}
