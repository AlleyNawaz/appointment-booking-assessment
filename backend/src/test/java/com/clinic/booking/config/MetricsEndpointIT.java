package com.clinic.booking.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PRD §14 Observability / Milestone 13 validation checklist: "Prometheus scrape endpoint
 * exposes all four named metrics" (booking success rate, hold-expiry rate, availability
 * query latency, flag-blocked-request count).
 *
 * <p>This test exposed a genuine defect: {@code management.prometheus.metrics.export.enabled}
 * was never set, so the {@code PrometheusMeterRegistry}/{@code PrometheusScrapeEndpoint} beans
 * were not reliably registered — {@code /actuator/prometheus} had no handler and fell through
 * to a generic {@code 403}. Fixed in {@code application.yml}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MetricsEndpointIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void prometheusEndpoint_exposesAllFourNamedMetrics() {
        String body = restTemplate.getForObject("/actuator/prometheus", String.class);

        assertThat(body)
                .contains("booking_appointments")
                .contains("booking_holds_expired")
                .contains("booking_availability_latency")
                .contains("booking_feature_flag_blocked");
    }
}
