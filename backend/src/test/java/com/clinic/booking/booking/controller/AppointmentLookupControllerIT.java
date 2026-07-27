package com.clinic.booking.booking.controller;

import com.clinic.booking.booking.domain.Appointment;
import com.clinic.booking.booking.dto.AppointmentResponse;
import com.clinic.booking.booking.dto.CreateAppointmentRequest;
import com.clinic.booking.booking.service.BookingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end verification of the never-gated patient self-service endpoints
 * (PRD §6/§8.7/§8.8) against the real local MySQL schema. Covers the
 * Milestone 6 validation checklist: AC-5, AC-7, the §15.3 anti-oracle
 * guarantee, active_slot_key release on cancellation (§19 #23), and the
 * PATIENT_SELF_SERVICE audit row (§7.9).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AppointmentLookupControllerIT {

    private static final String FLAG_SQL = "UPDATE feature_flags SET is_enabled = ? WHERE flag_name = 'enable_online_booking'";
    private static final long GENERAL_CONSULT_TYPE_ID = 2L; // seeded by V2, §7.2
    private static final Calendar UTC_CALENDAR = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BookingService bookingService;

    private long providerId;

    @BeforeEach
    void seedTestProviderWithAllDaysWorking() {
        String email = "appointment-lookup-it-" + UUID.randomUUID() + "@example.com";
        jdbcTemplate.update(
                "INSERT INTO providers (first_name, last_name, specialty, email, timezone, is_active) "
                        + "VALUES ('Ada', 'Okafor', 'General Medicine', ?, 'America/New_York', TRUE)",
                email);
        providerId = jdbcTemplate.queryForObject("SELECT id FROM providers WHERE email = ?", Long.class, email);
        for (int dayOfWeek = 0; dayOfWeek <= 6; dayOfWeek++) {
            jdbcTemplate.update(
                    "INSERT INTO provider_availability_rules (provider_id, day_of_week, start_time, end_time, rule_type) "
                            + "VALUES (?, ?, '00:00:00', '23:59:00', 'WORKING')",
                    providerId, dayOfWeek);
        }
        jdbcTemplate.update(FLAG_SQL, false);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM appointment_audit_log WHERE appointment_id IN "
                + "(SELECT id FROM appointments WHERE provider_id = ?)", providerId);
        jdbcTemplate.update("DELETE FROM appointments WHERE provider_id = ?", providerId);
        jdbcTemplate.update("DELETE FROM slot_holds WHERE provider_id = ?", providerId);
        jdbcTemplate.update("DELETE FROM provider_availability_rules WHERE provider_id = ?", providerId);
        jdbcTemplate.update("DELETE FROM providers WHERE id = ?", providerId);
        jdbcTemplate.update(FLAG_SQL, false);
    }

    @Test
    void ac5_flagOff_availabilityIs403_butAppointmentLookupIsStill200() {
        String token = insertAppointment(Instant.now().plus(10, ChronoUnit.DAYS), Appointment.Status.CONFIRMED, null);

        ResponseEntity<Map> availabilityResponse = restTemplate.getForEntity(
                "/api/v1/booking/availability?providerId=" + providerId + "&appointmentTypeId="
                        + GENERAL_CONSULT_TYPE_ID + "&date=2027-01-01",
                Map.class);
        assertThat(availabilityResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Map> lookupResponse = restTemplate.getForEntity(
                "/api/v1/booking/appointments/" + token, Map.class);
        assertThat(lookupResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(lookupResponse.getBody()).containsEntry("status", "CONFIRMED");
    }

    @Test
    void ac7_appointmentTwoHoursOut_deleteReturns409WithClinicPhoneNumber() {
        String token = insertAppointment(Instant.now().plus(2, ChronoUnit.HOURS), Appointment.Status.CONFIRMED, null);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/booking/appointments/" + token, HttpMethod.DELETE, null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("errorCode", "CANCELLATION_WINDOW_EXPIRED");
        assertThat((String) response.getBody().get("message")).contains("+1-555-0100");
    }

    @Test
    void malformedToken_andUnknownToken_returnIdenticalNotFoundResponse() {
        ResponseEntity<Map> malformedResponse = restTemplate.getForEntity(
                "/api/v1/booking/appointments/not-a-uuid", Map.class);
        ResponseEntity<Map> unknownResponse = restTemplate.getForEntity(
                "/api/v1/booking/appointments/" + UUID.randomUUID(), Map.class);

        assertThat(malformedResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unknownResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(malformedResponse.getBody().get("errorCode")).isEqualTo(unknownResponse.getBody().get("errorCode"));
        assertThat(malformedResponse.getBody().get("message")).isEqualTo(unknownResponse.getBody().get("message"));
    }

    @Test
    void malformedTokenContainingSlashes_stillReachesTheSameNotFoundResponse() {
        // Regression test for the Milestone 6 review defect: a plain single-segment
        // {confirmationToken} path pattern never matches a token with "/" in it, so it
        // fell through to Spring Boot's default error page instead of this uniform
        // APPOINTMENT_NOT_FOUND response — an observable anti-oracle break (§15.3).
        ResponseEntity<Map> unknownResponse = restTemplate.getForEntity(
                "/api/v1/booking/appointments/" + UUID.randomUUID(), Map.class);

        ResponseEntity<Map> slashResponse = restTemplate.getForEntity(
                "/api/v1/booking/appointments/foo/bar", Map.class);
        ResponseEntity<Map> multiSlashResponse = restTemplate.getForEntity(
                "/api/v1/booking/appointments/a/b/c/d", Map.class);
        ResponseEntity<Map> deleteSlashResponse = restTemplate.exchange(
                "/api/v1/booking/appointments/foo/bar", HttpMethod.DELETE, null, Map.class);

        for (ResponseEntity<Map> response : new ResponseEntity[] {slashResponse, multiSlashResponse, deleteSlashResponse}) {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().get("errorCode")).isEqualTo(unknownResponse.getBody().get("errorCode"));
            assertThat(response.getBody().get("message")).isEqualTo(unknownResponse.getBody().get("message"));
            assertThat(response.getBody()).containsKey("fieldErrors");
        }
    }

    @Test
    void successfulCancellation_freesActiveSlotKey_reBookingIsPermitted() {
        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);
        String token = insertAppointment(start, Appointment.Status.CONFIRMED, null);

        ResponseEntity<Map> cancelResponse = restTemplate.exchange(
                "/api/v1/booking/appointments/" + token, HttpMethod.DELETE, null, Map.class);
        assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelResponse.getBody()).containsEntry("status", "CANCELLED");

        String holdToken = insertHold(start);
        AppointmentResponse rebooked = bookingService.createAppointment(
                new CreateAppointmentRequest(holdToken, "Sam Lee", "sam@example.com", "+14155559999", null),
                UUID.randomUUID().toString());

        assertThat(rebooked.status()).isEqualTo(Appointment.Status.CONFIRMED);
    }

    @Test
    void cancellation_writesAuditRow_withPatientSelfServiceAndSuppliedReason() {
        String token = insertAppointment(Instant.now().plus(10, ChronoUnit.DAYS), Appointment.Status.CONFIRMED, null);

        HttpEntity<Map<String, String>> body = new HttpEntity<>(Map.of("reason", "Schedule conflict"));
        restTemplate.exchange("/api/v1/booking/appointments/" + token, HttpMethod.DELETE, body, Map.class);

        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT changed_by, reason, previous_status, new_status FROM appointment_audit_log a "
                        + "JOIN appointments ap ON ap.id = a.appointment_id WHERE ap.confirmation_token = ?",
                token);
        assertThat(audit.get("changed_by")).isEqualTo("PATIENT_SELF_SERVICE");
        assertThat(audit.get("reason")).isEqualTo("Schedule conflict");
        assertThat(audit.get("previous_status")).isEqualTo("CONFIRMED");
        assertThat(audit.get("new_status")).isEqualTo("CANCELLED");
    }

    @Test
    void cancellation_withNoReasonSupplied_writesAuditRowWithNullReason() {
        String token = insertAppointment(Instant.now().plus(10, ChronoUnit.DAYS), Appointment.Status.CONFIRMED, null);

        restTemplate.exchange("/api/v1/booking/appointments/" + token, HttpMethod.DELETE, null, Map.class);

        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT reason FROM appointment_audit_log a "
                        + "JOIN appointments ap ON ap.id = a.appointment_id WHERE ap.confirmation_token = ?",
                token);
        assertThat(audit.get("reason")).isNull();
    }

    private String insertAppointment(Instant start, Appointment.Status status, String notes) {
        String confirmationToken = UUID.randomUUID().toString();
        Instant end = start.plus(30, ChronoUnit.MINUTES);
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO appointments (confirmation_token, provider_id, appointment_type_id, "
                            + "patient_full_name, patient_email, patient_phone, notes, start_datetime, end_datetime, "
                            + "status, idempotency_key, request_body_hash) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            ps.setString(1, confirmationToken);
            ps.setLong(2, providerId);
            ps.setLong(3, GENERAL_CONSULT_TYPE_ID);
            ps.setString(4, "Jordan Rivera");
            ps.setString(5, "jordan@example.com");
            ps.setString(6, "+14155551234");
            ps.setString(7, notes);
            ps.setTimestamp(8, Timestamp.from(start), UTC_CALENDAR);
            ps.setTimestamp(9, Timestamp.from(end), UTC_CALENDAR);
            ps.setString(10, status.name());
            ps.setString(11, UUID.randomUUID().toString());
            ps.setString(12, "0".repeat(64));
            return ps;
        });
        return confirmationToken;
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
}
