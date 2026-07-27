package com.clinic.booking.booking.service;

import com.clinic.booking.booking.dto.HoldResponse;
import com.clinic.booking.common.exception.ProviderUnavailableException;
import com.clinic.booking.common.exception.SlotAlreadyBookedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link HoldService} (PRD §8.5/§10/§12.9/§12.10)
 * against the real local MySQL schema — same pattern established in
 * Milestones 1-3 (no Testcontainers dependency introduced). Milestone 1 seeds
 * no provider rows, so each test seeds and removes its own via raw JDBC,
 * mirroring how other IT tests manipulate {@code feature_flags} directly.
 */
@SpringBootTest
class HoldServiceIT {

    private static final long GENERAL_CONSULT_TYPE_ID = 2L; // seeded by V2, §7.2

    @Autowired
    private HoldService holdService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long providerId;

    @BeforeEach
    void seedTestProvider() {
        String email = "hold-service-it-" + UUID.randomUUID() + "@example.com";
        jdbcTemplate.update(
                "INSERT INTO providers (first_name, last_name, specialty, email, timezone, is_active) "
                        + "VALUES ('Test', 'Provider', 'General Medicine', ?, 'America/New_York', TRUE)",
                email);
        providerId = jdbcTemplate.queryForObject("SELECT id FROM providers WHERE email = ?", Long.class, email);
    }

    @AfterEach
    void cleanUpTestProvider() {
        jdbcTemplate.update("DELETE FROM slot_holds WHERE provider_id = ?", providerId);
        jdbcTemplate.update("DELETE FROM providers WHERE id = ?", providerId);
    }

    @Test
    void createHold_ttlIsExactlyHoldDurationMinutes() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant beforeCall = Instant.now();

        HoldResponse response = holdService.createHold(providerId, GENERAL_CONSULT_TYPE_ID, start);

        Instant afterCall = Instant.now();
        assertThat(response.expiresAt())
                .isAfterOrEqualTo(beforeCall.plusSeconds(5 * 60))
                .isBeforeOrEqualTo(afterCall.plusSeconds(5 * 60));
    }

    @Test
    void concurrentHoldRequestsForTheSameSlot_exactlyOneSucceeds() throws InterruptedException {
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
        int attempts = 5;
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        List<Future<HoldResponse>> futures = new ArrayList<>();

        for (int i = 0; i < attempts; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                go.await();
                return holdService.createHold(providerId, GENERAL_CONSULT_TYPE_ID, start);
            }));
        }

        ready.await(5, TimeUnit.SECONDS);
        go.countDown(); // release all threads at once, maximizing the chance of a genuine race

        int successCount = 0;
        int conflictCount = 0;
        for (Future<HoldResponse> future : futures) {
            try {
                future.get(10, TimeUnit.SECONDS);
                successCount++;
            } catch (ExecutionException e) {
                if (e.getCause() instanceof SlotAlreadyBookedException) {
                    conflictCount++;
                } else {
                    throw new AssertionError("Unexpected failure", e.getCause());
                }
            } catch (TimeoutException e) {
                throw new AssertionError("Hold creation timed out", e);
            }
        }
        executor.shutdown();

        // §10: the unique constraint on slot_holds(provider_id, start_datetime) is the
        // actual arbiter — insert-and-catch, not a pre-check-then-insert race.
        assertThat(successCount).isEqualTo(1);
        assertThat(conflictCount).isEqualTo(attempts - 1);
    }

    @Test
    void inactiveProvider_throwsProviderUnavailableException() {
        jdbcTemplate.update("UPDATE providers SET is_active = FALSE WHERE id = ?", providerId);
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS);

        assertThatThrownBy(() -> holdService.createHold(providerId, GENERAL_CONSULT_TYPE_ID, start))
                .isInstanceOf(ProviderUnavailableException.class);
    }

    @Test
    void unknownAppointmentType_throwsProviderUnavailableException() {
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS);

        assertThatThrownBy(() -> holdService.createHold(providerId, 9_999L, start))
                .isInstanceOf(ProviderUnavailableException.class);
    }
}
