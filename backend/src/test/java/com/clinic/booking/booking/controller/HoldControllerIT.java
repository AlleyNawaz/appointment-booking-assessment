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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end verification of POST /api/v1/booking/holds (PRD §6/§8.5) against
 * the real local MySQL schema, added following the Milestone 4 review, which
 * found (and this class proves fixed) two defects: a missing request body
 * bypassed {@code @FeatureGate} entirely (Spring's default
 * {@code @RequestBody} throws during argument resolution, before the aspect
 * ever runs), and a malformed/missing body leaked Spring Boot's default error
 * page instead of the PRD §8 error envelope. Same pattern as
 * {@link BookingConfigControllerIT}: {@code RANDOM_PORT} + real HTTP calls,
 * {@code @DirtiesContext} per test so {@code FeatureFlagService}'s 10-second
 * cache never carries a stale value between tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class HoldControllerIT {

    private static final String FLAG_SQL = "UPDATE feature_flags SET is_enabled = ? WHERE flag_name = 'enable_online_booking'";
    private static final long GENERAL_CONSULT_TYPE_ID = 2L; // seeded by V2, §7.2
    private static final String HOLDS_URL = "/api/v1/booking/holds";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void resetFlagToSeededOffState() {
        jdbcTemplate.update(FLAG_SQL, false);
    }

    @Test
    void createHold_whenFlagOff_returns403FeatureDisabled() {
        jdbcTemplate.update(FLAG_SQL, false);

        ResponseEntity<Map> response = restTemplate.postForEntity(HOLDS_URL, jsonEntity(validBody()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("errorCode", "FEATURE_DISABLED");
    }

    @Test
    void createHold_missingBody_whenFlagOff_returns403FeatureDisabled_notBadRequest() {
        // The regression this proves: @RequestBody(required = false) means a missing
        // body no longer throws during argument resolution, so the flag check (which
        // runs as part of the method invocation itself) still wins the race (§6).
        jdbcTemplate.update(FLAG_SQL, false);
        HttpEntity<Void> noBody = new HttpEntity<>(jsonHeaders());

        ResponseEntity<Map> response = restTemplate.postForEntity(HOLDS_URL, noBody, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("errorCode", "FEATURE_DISABLED");
    }

    @Test
    void createHold_missingBody_whenFlagOn_returns400ValidationError() {
        jdbcTemplate.update(FLAG_SQL, true);
        HttpEntity<Void> noBody = new HttpEntity<>(jsonHeaders());

        ResponseEntity<Map> response = restTemplate.postForEntity(HOLDS_URL, noBody, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("errorCode", "VALIDATION_ERROR");
    }

    @Test
    void createHold_malformedJson_whenFlagOn_returns400ValidationError_notSpringDefaultError() {
        // Proves the envelope fix: previously this leaked Spring Boot's raw default
        // error body ({"timestamp","status","error","path"}, no "errorCode") instead
        // of the PRD §8 envelope.
        jdbcTemplate.update(FLAG_SQL, true);
        HttpEntity<String> malformed = new HttpEntity<>("{ this is not valid json", jsonHeaders());

        ResponseEntity<Map> response = restTemplate.postForEntity(HOLDS_URL, malformed, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("errorCode", "VALIDATION_ERROR");
        assertThat(response.getBody()).containsKey("message");
    }

    @Test
    void createHold_validRequest_whenFlagOn_returns201WithHoldTokenAndExpiresAt() {
        jdbcTemplate.update(FLAG_SQL, true);
        String email = "hold-controller-it-" + UUID.randomUUID() + "@example.com";
        jdbcTemplate.update(
                "INSERT INTO providers (first_name, last_name, specialty, email, timezone, is_active) "
                        + "VALUES ('Test', 'Provider', 'General Medicine', ?, 'America/New_York', TRUE)",
                email);
        long providerId = jdbcTemplate.queryForObject("SELECT id FROM providers WHERE email = ?", Long.class, email);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    HOLDS_URL, jsonEntity(validBody(providerId)), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).containsKeys("holdToken", "expiresAt");
        } finally {
            jdbcTemplate.update("DELETE FROM slot_holds WHERE provider_id = ?", providerId);
            jdbcTemplate.update("DELETE FROM providers WHERE id = ?", providerId);
        }
    }

    private static Map<String, Object> validBody() {
        return validBody(5L);
    }

    private static Map<String, Object> validBody(long providerId) {
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
        return Map.of(
                "providerId", providerId,
                "appointmentTypeId", GENERAL_CONSULT_TYPE_ID,
                "startDateTime", start.toString());
    }

    private static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private static <T> HttpEntity<T> jsonEntity(T body) {
        return new HttpEntity<>(body, jsonHeaders());
    }
}
