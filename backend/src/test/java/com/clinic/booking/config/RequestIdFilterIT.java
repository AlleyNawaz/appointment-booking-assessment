package com.clinic.booking.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end verification of {@link RequestIdFilter} (PRD §14 Observability) — applies to
 * every request regardless of path, so {@code /actuator/health} (always {@code permitAll},
 * no DB seeding needed) is a stable, endpoint-agnostic target.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RequestIdFilterIT {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void requestWithNoIncomingHeader_getsAGeneratedRequestIdOnTheResponse() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

        String requestId = response.getHeaders().getFirst(RequestIdFilter.REQUEST_ID_HEADER);
        assertThat(requestId).isNotBlank();
        assertThat(UUID_PATTERN.matcher(requestId).matches()).isTrue();
    }

    @Test
    void requestWithAnIncomingRequestId_isEchoedBackUnchanged_notReplaced() {
        String clientRequestId = "client-supplied-" + UUID.randomUUID();
        HttpHeaders headers = new HttpHeaders();
        headers.set(RequestIdFilter.REQUEST_ID_HEADER, clientRequestId);

        ResponseEntity<String> response =
                restTemplate.exchange("/actuator/health", org.springframework.http.HttpMethod.GET,
                        new HttpEntity<>(headers), String.class);

        assertThat(response.getHeaders().getFirst(RequestIdFilter.REQUEST_ID_HEADER)).isEqualTo(clientRequestId);
    }

    @Test
    void twoRequestsWithNoIncomingHeader_eachGetADifferentGeneratedId() {
        String first = restTemplate.getForEntity("/actuator/health", String.class)
                .getHeaders().getFirst(RequestIdFilter.REQUEST_ID_HEADER);
        String second = restTemplate.getForEntity("/actuator/health", String.class)
                .getHeaders().getFirst(RequestIdFilter.REQUEST_ID_HEADER);

        assertThat(first).isNotEqualTo(second);
    }
}
