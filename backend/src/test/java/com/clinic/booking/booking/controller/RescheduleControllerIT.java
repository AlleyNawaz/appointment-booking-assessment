package com.clinic.booking.booking.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
 * End-to-end verification that POST /api/v1/booking/appointments/{token}/reschedule
 * (PRD §6/§8.19) is wired into the six-endpoint gated list — same pattern as
 * {@link HoldControllerIT}: {@code RANDOM_PORT} + real HTTP calls,
 * {@code @DirtiesContext} per test so {@code FeatureFlagService}'s 10-second
 * cache never carries a stale value between tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RescheduleControllerIT {

    private static final String FLAG_SQL = "UPDATE feature_flags SET is_enabled = ? WHERE flag_name = 'enable_online_booking'";
    private static final long GENERAL_CONSULT_TYPE_ID = 2L; // seeded by V2, §7.2
    private static final Calendar UTC_CALENDAR = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long providerId;

    @AfterEach
    void resetFlagToSeededOffState() {
        jdbcTemplate.update(FLAG_SQL, false);
        if (providerId != 0) {
            jdbcTemplate.update("DELETE FROM appointment_audit_log WHERE appointment_id IN "
                    + "(SELECT id FROM appointments WHERE provider_id = ?)", providerId);
            jdbcTemplate.update("DELETE FROM appointments WHERE provider_id = ?", providerId);
            jdbcTemplate.update("DELETE FROM slot_holds WHERE provider_id = ?", providerId);
            jdbcTemplate.update("DELETE FROM provider_availability_rules WHERE provider_id = ?", providerId);
            jdbcTemplate.update("DELETE FROM providers WHERE id = ?", providerId);
        }
    }

    @Test
    void reschedule_whenFlagOff_returns403FeatureDisabled_beforeAnyOtherValidation() {
        jdbcTemplate.update(FLAG_SQL, false);
        HttpEntity<Map<String, Object>> entity = jsonEntity(Map.of("holdToken", UUID.randomUUID().toString()));

        // A confirmation token that doesn't exist — proves the flag check runs first
        // (§6's Backend row), before the (otherwise 404-triggering) appointment lookup.
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/booking/appointments/" + UUID.randomUUID() + "/reschedule", entity, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("errorCode", "FEATURE_DISABLED");
    }

    @Test
    void reschedule_missingIdempotencyKey_whenFlagOff_returns403FeatureDisabled_notBadRequest() {
        jdbcTemplate.update(FLAG_SQL, false);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity =
                new HttpEntity<>(Map.of("holdToken", UUID.randomUUID().toString()), headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/booking/appointments/" + UUID.randomUUID() + "/reschedule", entity, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("errorCode", "FEATURE_DISABLED");
    }

    @Test
    void reschedule_malformedTokenContainingSlash_andWellFormedNonexistentToken_bothReturn404NotFound() {
        jdbcTemplate.update(FLAG_SQL, true);
        HttpEntity<Map<String, Object>> entity = jsonEntity(Map.of("holdToken", UUID.randomUUID().toString()));

        // §15.3: a token containing "/" must reach the controller (not Spring's default error
        // handling) and produce the exact same envelope/status as a well-formed-but-unknown one.
        ResponseEntity<Map> malformed = restTemplate.postForEntity(
                "/api/v1/booking/appointments/abc/def/reschedule", entity, Map.class);
        ResponseEntity<Map> wellFormedButUnknown = restTemplate.postForEntity(
                "/api/v1/booking/appointments/" + UUID.randomUUID() + "/reschedule", entity, Map.class);

        assertThat(malformed.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(malformed.getBody()).containsEntry("errorCode", "APPOINTMENT_NOT_FOUND");
        assertThat(wellFormedButUnknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(wellFormedButUnknown.getBody()).containsEntry("errorCode", "APPOINTMENT_NOT_FOUND");
        assertThat(malformed.getBody().get("errorCode")).isEqualTo(wellFormedButUnknown.getBody().get("errorCode"));
    }

    @Test
    void reschedule_validRequest_whenFlagOn_stillCompletesSuccessfullyThroughTheCaptureAllRoute() {
        jdbcTemplate.update(FLAG_SQL, true);
        seedProviderWithAllDaysWorking();
        String originalHoldToken = insertHold(Instant.now().plus(5, ChronoUnit.DAYS));
        ResponseEntity<Map> booking = restTemplate.postForEntity(
                "/api/v1/booking/appointments",
                jsonEntity(Map.of(
                        "holdToken", originalHoldToken,
                        "patientFullName", "Jordan Rivera",
                        "patientEmail", "reschedule-controller-it@example.com",
                        "patientPhone", "+14155551234"),
                        UUID.randomUUID().toString()),
                Map.class);
        assertThat(booking.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String originalToken = (String) booking.getBody().get("confirmationToken");

        String newHoldToken = insertHold(Instant.now().plus(8, ChronoUnit.DAYS));
        ResponseEntity<Map> reschedule = restTemplate.postForEntity(
                "/api/v1/booking/appointments/" + originalToken + "/reschedule",
                jsonEntity(Map.of("holdToken", newHoldToken)),
                Map.class);

        assertThat(reschedule.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(reschedule.getBody()).containsEntry("previousConfirmationToken", originalToken);
        assertThat(reschedule.getBody()).containsEntry("status", "CONFIRMED");
    }

    private void seedProviderWithAllDaysWorking() {
        String email = "reschedule-controller-it-" + UUID.randomUUID() + "@example.com";
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

    private static HttpEntity<Map<String, Object>> jsonEntity(Map<String, Object> body) {
        return jsonEntity(body, null);
    }

    private static HttpEntity<Map<String, Object>> jsonEntity(Map<String, Object> body, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey == null ? UUID.randomUUID().toString() : idempotencyKey);
        return new HttpEntity<>(body, headers);
    }
}
