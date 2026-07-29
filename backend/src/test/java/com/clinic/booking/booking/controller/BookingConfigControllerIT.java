package com.clinic.booking.booking.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end verification of PRD §6/§8.1/§8.2/§8.3/§8.4 against the real local
 * MySQL schema. Each test flips {@code feature_flags} via a
 * raw SQL update (simulating an external state change, not going through
 * application code) and the context is reloaded after every test
 * ({@link DirtiesContext}) so each test starts with a cold, empty
 * {@link com.clinic.booking.config.FeatureFlagService} cache — otherwise a
 * prior test's cached flag value could still be "fresh" (&lt;10s old) and
 * make results order-dependent.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BookingConfigControllerIT {

    private static final String FLAG_SQL = "UPDATE feature_flags SET is_enabled = ? WHERE flag_name = 'enable_online_booking'";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void resetFlagToSeededOffState() {
        jdbcTemplate.update(FLAG_SQL, false);
    }

    @Test
    void config_isNeverGated_andReflectsTheFlag() {
        jdbcTemplate.update(FLAG_SQL, false);

        ResponseEntity<Map> response = restTemplate.getForEntity("/api/v1/booking/config", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("enabled", false);
    }

    @Test
    void gatedEndpoints_return403FeatureDisabled_whenFlagIsOff() {
        jdbcTemplate.update(FLAG_SQL, false);

        ResponseEntity<Map> typesResponse =
                restTemplate.getForEntity("/api/v1/booking/appointment-types", Map.class);
        assertThat(typesResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(typesResponse.getBody()).containsEntry("errorCode", "FEATURE_DISABLED");

        ResponseEntity<Map> providersResponse =
                restTemplate.getForEntity("/api/v1/booking/providers?appointmentTypeId=2", Map.class);
        assertThat(providersResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(providersResponse.getBody()).containsEntry("errorCode", "FEATURE_DISABLED");

        ResponseEntity<Map> availabilityResponse = restTemplate.getForEntity(
                "/api/v1/booking/availability?providerId=5&appointmentTypeId=2&date=" + tomorrow(), Map.class);
        assertThat(availabilityResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(availabilityResponse.getBody()).containsEntry("errorCode", "FEATURE_DISABLED");
    }

    @Test
    @SuppressWarnings("unchecked")
    void gatedEndpoints_succeedWithDtoShapedResponses_whenFlagIsOn() {
        jdbcTemplate.update(FLAG_SQL, true);

        ResponseEntity<List> typesResponse =
                restTemplate.getForEntity("/api/v1/booking/appointment-types", List.class);
        assertThat(typesResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(typesResponse.getBody()).hasSize(4);

        Map<String, Object> generalConsult = (Map<String, Object>) typesResponse.getBody().stream()
                .filter(t -> "GENERAL_CONSULT".equals(((Map<?, ?>) t).get("code")))
                .findFirst()
                .orElseThrow();
        assertThat(generalConsult).containsEntry("displayName", "General Consultation");
        assertThat(generalConsult).containsEntry("durationMinutes", 30);
        assertThat(generalConsult).containsEntry("requiresApproval", false);
        assertThat(generalConsult).doesNotContainKey("bufferMinutes");
        assertThat(generalConsult).doesNotContainKey("isActive");

        ResponseEntity<List> providersResponse =
                restTemplate.getForEntity("/api/v1/booking/providers?appointmentTypeId=2", List.class);
        assertThat(providersResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        // V14's demo seed maps a real provider to every appointment type, so this list is no
        // longer expected to be empty — assert DTO shape instead (id/firstName/lastName/specialty
        // only, no internal-only fields like email/isActive/deletedAt leaking out).
        assertThat(providersResponse.getBody()).isNotEmpty();
        Map<String, Object> firstProvider = (Map<String, Object>) providersResponse.getBody().get(0);
        assertThat(firstProvider).containsOnlyKeys("id", "firstName", "lastName", "specialty");

        ResponseEntity<Map> availabilityResponse = restTemplate.getForEntity(
                "/api/v1/booking/availability?providerId=5&appointmentTypeId=2&date=" + tomorrow(), Map.class);
        assertThat(availabilityResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(availabilityResponse.getBody()).containsEntry("date", tomorrow().toString());
        // providerId=5 is a placeholder id with no availability rules of its own (the demo seed
        // uses different, higher provider ids) — empty slots for this specific id is still correct.
        assertThat((List<?>) availabilityResponse.getBody().get("slots")).isEmpty();
    }

    @Test
    void providers_missingAppointmentTypeId_whenFlagOff_returns403FeatureDisabled_notValidationError() {
        // Proves the ordering fix: the flag check must win over the missing-parameter
        // check, since §6 requires the flag to be checked "as the first statement
        // before any other validation."
        jdbcTemplate.update(FLAG_SQL, false);

        ResponseEntity<Map> response = restTemplate.getForEntity("/api/v1/booking/providers", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("errorCode", "FEATURE_DISABLED");
    }

    @Test
    void providers_missingAppointmentTypeId_whenFlagOn_returns400ValidationError() {
        // Once the flag check has passed, the missing-parameter validation still
        // applies, with the same error shape as before the ordering fix.
        jdbcTemplate.update(FLAG_SQL, true);

        ResponseEntity<Map> response = restTemplate.getForEntity("/api/v1/booking/providers", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("errorCode", "VALIDATION_ERROR");
    }

    @Test
    void availability_missingParams_whenFlagOff_returns403FeatureDisabled_notValidationError() {
        // Same ordering-safety pattern as /providers: the flag check must win over
        // any missing-parameter check (§6).
        jdbcTemplate.update(FLAG_SQL, false);

        ResponseEntity<Map> response = restTemplate.getForEntity("/api/v1/booking/availability", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("errorCode", "FEATURE_DISABLED");
    }

    @Test
    void availability_missingParams_whenFlagOn_returns400ValidationError() {
        jdbcTemplate.update(FLAG_SQL, true);

        ResponseEntity<Map> response = restTemplate.getForEntity("/api/v1/booking/availability", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("errorCode", "VALIDATION_ERROR");
    }

    @Test
    void availability_dateInPast_whenFlagOn_returns400InvalidAppointmentDate() {
        jdbcTemplate.update(FLAG_SQL, true);
        LocalDate yesterday = clinicToday().minusDays(2);

        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/api/v1/booking/availability?providerId=5&appointmentTypeId=2&date=" + yesterday, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("errorCode", "INVALID_APPOINTMENT_DATE");
    }

    @Test
    void availability_dateBeyondBookingWindow_whenFlagOn_returns400BookingWindowExceeded() {
        jdbcTemplate.update(FLAG_SQL, true);
        LocalDate tooFarOut = clinicToday().plusDays(95);

        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/api/v1/booking/availability?providerId=5&appointmentTypeId=2&date=" + tooFarOut, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("errorCode", "BOOKING_WINDOW_EXCEEDED");
    }

    /**
     * Matches the clinic timezone the service validates against (application.yml's
     * default {@code booking.clinic-timezone}), not the test JVM's system default
     * zone — the two can disagree by a calendar day near local midnight.
     */
    private static LocalDate clinicToday() {
        return LocalDate.now(ZoneId.of("America/New_York"));
    }

    private static LocalDate tomorrow() {
        return clinicToday().plusDays(1);
    }
}
