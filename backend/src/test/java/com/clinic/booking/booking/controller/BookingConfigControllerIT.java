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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end verification of PRD §6/§8.1/§8.2/§8.3 against the real local
 * MySQL schema from Milestone 1. Each test flips {@code feature_flags} via a
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
        assertThat(providersResponse.getBody()).isEmpty(); // no providers seeded yet (Milestone 1 seeds no provider rows)
    }

    @Test
    void providers_missingAppointmentTypeId_returns400ValidationError_regardlessOfFlagState() {
        jdbcTemplate.update(FLAG_SQL, false);

        ResponseEntity<Map> response = restTemplate.getForEntity("/api/v1/booking/providers", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("errorCode", "VALIDATION_ERROR");
    }
}
