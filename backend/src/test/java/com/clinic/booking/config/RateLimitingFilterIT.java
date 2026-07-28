package com.clinic.booking.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end verification of {@link RateLimitingFilter} (PRD §15.7/§12.5/§19 #32) against
 * the real local MySQL schema, using the actual configured limits (10/min on
 * {@code GET /booking/availability}; 5/10min combined on {@code POST /booking/holds} +
 * {@code POST /booking/appointments}) rather than test-only overrides. {@code RANDOM_PORT}
 * + real HTTP calls, same pattern as {@link com.clinic.booking.booking.controller.HoldControllerIT}.
 * {@code @DirtiesContext} per test since the filter's per-IP windows are in-memory and would
 * otherwise leak counts between test methods (every request in this test class originates
 * from the same loopback address).
 *
 * <p>This test exposed a genuine defect: {@code RateLimitingFilter.writeRateLimited} originally
 * wrote its 429 body via {@code response.getWriter()} with no explicit {@code Content-Length},
 * which the client's connection-reuse layer treated as a malformed response, entering a long
 * automatic retry loop on the next request. Fixed by writing fixed-length bytes via
 * {@code response.getOutputStream()} instead.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RateLimitingFilterIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private BookingProperties bookingProperties;

    @Test
    void availability_firstTenRequestsPerMinute_areAllowed_eleventhIsRateLimited() {
        int limit = bookingProperties.getAvailabilityRateLimitPerMinute();
        String url = "/api/v1/booking/availability?providerId=1&appointmentTypeId=1&date=2026-08-01";

        for (int i = 0; i < limit; i++) {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, org.springframework.http.HttpMethod.GET, jsonEntity(null), Map.class);
            assertThat(response.getStatusCode().value()).isNotEqualTo(429);
        }

        ResponseEntity<Map> limited =
                restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, jsonEntity(null), Map.class);

        assertThat(limited.getStatusCode().value()).isEqualTo(429);
        assertThat(limited.getBody()).containsEntry("errorCode", "RATE_LIMITED");
        assertThat(limited.getHeaders().getFirst("Retry-After")).isNotNull();
        assertThat(Long.parseLong(limited.getHeaders().getFirst("Retry-After"))).isPositive();
    }

    @Test
    void holdsAndAppointments_combinedLimitOfFivePerTenMinutes_isSharedAcrossBothEndpoints() {
        int limit = bookingProperties.getBookingRateLimitPerTenMinutes();

        // Alternate between the two endpoints the rule combines — proves one shared counter,
        // not two independent ones each allowing `limit` requests.
        for (int i = 0; i < limit; i++) {
            String url = i % 2 == 0 ? "/api/v1/booking/holds" : "/api/v1/booking/appointments";
            ResponseEntity<Map> response = restTemplate.postForEntity(url, jsonEntity(Map.of()), Map.class);
            assertThat(response.getStatusCode().value()).isNotEqualTo(429);
        }

        ResponseEntity<Map> limited =
                restTemplate.postForEntity("/api/v1/booking/holds", jsonEntity(Map.of()), Map.class);

        assertThat(limited.getStatusCode().value()).isEqualTo(429);
        assertThat(limited.getBody()).containsEntry("errorCode", "RATE_LIMITED");
        assertThat(limited.getHeaders().getFirst("Retry-After")).isNotNull();
    }

    @Test
    void reschedule_isNotSubjectToTheHoldsAndAppointmentsLimit() {
        // §15.7 names exactly two endpoints — the sixth gated endpoint (§8.19) added in
        // Milestone 12 is deliberately not one of them.
        int limit = bookingProperties.getBookingRateLimitPerTenMinutes();
        String rescheduleUrl = "/api/v1/booking/appointments/00000000-0000-0000-0000-000000000000/reschedule";

        for (int i = 0; i <= limit; i++) {
            ResponseEntity<Map> response =
                    restTemplate.postForEntity(rescheduleUrl, jsonEntity(Map.of()), Map.class);
            assertThat(response.getStatusCode().value()).isNotEqualTo(429);
        }
    }

    private static <T> HttpEntity<T> jsonEntity(T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
