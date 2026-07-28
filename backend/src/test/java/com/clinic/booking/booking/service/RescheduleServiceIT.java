package com.clinic.booking.booking.service;

import com.clinic.booking.audit.AppointmentAuditLog;
import com.clinic.booking.audit.AppointmentAuditLogRepository;
import com.clinic.booking.booking.domain.Appointment;
import com.clinic.booking.booking.dto.AppointmentResponse;
import com.clinic.booking.booking.dto.CreateAppointmentRequest;
import com.clinic.booking.booking.dto.RescheduleResponse;
import com.clinic.booking.common.exception.AppointmentNotReschedulableException;
import com.clinic.booking.common.exception.AppointmentStateChangedException;
import com.clinic.booking.common.exception.CancellationWindowExpiredException;
import com.clinic.booking.common.exception.LeadTimeViolationException;
import com.clinic.booking.common.exception.PatientDailyLimitExceededException;
import com.clinic.booking.common.exception.SlotAlreadyBookedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Calendar;
import java.util.TimeZone;
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
 * Integration tests for {@link RescheduleService} (PRD §8.19/§12.13) against the real local
 * MySQL schema — same pattern established in Milestones 1-11. Covers the Milestone 12
 * validation checklist: AC-11 (concurrent race for the same new slot, full-transaction
 * rollback), §19 #51 (concurrent write to the original row → APPOINTMENT_STATE_CHANGED, not
 * STALE_VERSION), §19 #52 (own daily-limit/duplicate exclusion), §19 #39/#40 (lead time /
 * cancellation cutoff), APPOINTMENT_NOT_RESCHEDULABLE, the two-audit-row contract, and
 * idempotent replay.
 */
@SpringBootTest
class RescheduleServiceIT {

    private static final long GENERAL_CONSULT_TYPE_ID = 2L; // requires_approval = FALSE
    private static final long NEW_PATIENT_TYPE_ID = 1L; // requires_approval = TRUE
    private static final ZoneId ZONE = ZoneId.of("America/New_York");
    private static final Calendar UTC_CALENDAR = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

    @Autowired
    private BookingService bookingService;

    @Autowired
    private RescheduleService rescheduleService;

    @Autowired
    private AppointmentAuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long providerId;

    @BeforeEach
    void seedTestProviderWithAllDaysWorking() {
        String email = "reschedule-service-it-" + UUID.randomUUID() + "@example.com";
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
        jdbcTemplate.update(
                "DELETE FROM appointment_audit_log WHERE appointment_id IN "
                        + "(SELECT id FROM appointments WHERE provider_id = ?)", providerId);
        jdbcTemplate.update("DELETE FROM appointments WHERE provider_id = ?", providerId);
        jdbcTemplate.update("DELETE FROM slot_holds WHERE provider_id = ?", providerId);
        jdbcTemplate.update("DELETE FROM provider_availability_rules WHERE provider_id = ?", providerId);
        jdbcTemplate.update("DELETE FROM providers WHERE id = ?", providerId);
    }

    @Test
    void successfulReschedule_cancelsOriginal_createsNewConfirmed_writesTwoAuditRows_deletesHold() {
        AppointmentResponse original = createOriginal(nextWorkingInstant(5));
        String newHoldToken = insertHold(GENERAL_CONSULT_TYPE_ID, nextWorkingInstant(8));

        RescheduleResponse response = rescheduleService.reschedule(
                original.confirmationToken(), newHoldToken, UUID.randomUUID().toString(), "Schedule conflict");

        assertThat(response.status()).isEqualTo(Appointment.Status.CONFIRMED);
        assertThat(response.previousConfirmationToken()).isEqualTo(original.confirmationToken());
        assertThat(response.confirmationToken()).isNotEqualTo(original.confirmationToken());

        String originalStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM appointments WHERE confirmation_token = ?",
                String.class, original.confirmationToken());
        assertThat(originalStatus).isEqualTo("CANCELLED");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM slot_holds WHERE hold_token = ?", Integer.class, newHoldToken))
                .isEqualTo(0);

        long originalId = jdbcTemplate.queryForObject(
                "SELECT id FROM appointments WHERE confirmation_token = ?", Long.class, original.confirmationToken());
        long newId = jdbcTemplate.queryForObject(
                "SELECT id FROM appointments WHERE confirmation_token = ?", Long.class, response.confirmationToken());

        AppointmentAuditLog cancelRow = auditLogRepository.findAll().stream()
                .filter(row -> row.getAppointmentId().equals(originalId)).findFirst().orElseThrow();
        assertThat(cancelRow.getPreviousStatus()).isEqualTo("CONFIRMED");
        assertThat(cancelRow.getNewStatus()).isEqualTo("CANCELLED");
        assertThat(cancelRow.getChangedBy()).isEqualTo("PATIENT_SELF_SERVICE");
        assertThat(cancelRow.getReason()).isEqualTo("Schedule conflict");

        AppointmentAuditLog newRow = auditLogRepository.findAll().stream()
                .filter(row -> row.getAppointmentId().equals(newId)).findFirst().orElseThrow();
        assertThat(newRow.getPreviousStatus()).isNull();
        assertThat(newRow.getNewStatus()).isEqualTo("CONFIRMED");
        assertThat(newRow.getChangedBy()).isEqualTo("PATIENT_SELF_SERVICE");
    }

    @Test
    void ac11_concurrentReschedulesForTheSameNewSlot_exactlyOneSucceeds_loserOriginalRemainsConfirmed()
            throws InterruptedException {
        AppointmentResponse original1 = createOriginal(nextWorkingInstant(5));
        AppointmentResponse original2 = createOriginal(
                nextWorkingInstant(6), "other-patient-" + UUID.randomUUID() + "@example.com", "+14155559999");
        String sharedNewHoldToken = insertHold(GENERAL_CONSULT_TYPE_ID, nextWorkingInstant(9));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<RescheduleResponse> future1 = executor.submit(() -> {
            ready.countDown();
            go.await();
            return rescheduleService.reschedule(
                    original1.confirmationToken(), sharedNewHoldToken, UUID.randomUUID().toString(), null);
        });
        Future<RescheduleResponse> future2 = executor.submit(() -> {
            ready.countDown();
            go.await();
            return rescheduleService.reschedule(
                    original2.confirmationToken(), sharedNewHoldToken, UUID.randomUUID().toString(), null);
        });
        ready.await(5, TimeUnit.SECONDS);
        go.countDown();

        int successCount = 0;
        int conflictCount = 0;
        for (Future<RescheduleResponse> future : List.of(future1, future2)) {
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
                throw new AssertionError("Reschedule timed out", e);
            }
        }
        executor.shutdown();

        assertThat(successCount).isEqualTo(1);
        assertThat(conflictCount).isEqualTo(1);

        // The loser's original appointment must be restored to CONFIRMED — proof that a
        // losing race rolls back the ENTIRE transaction, including the cancel-leg (§12.13 step 7).
        List<String> statuses = jdbcTemplate.queryForList(
                "SELECT status FROM appointments WHERE confirmation_token IN (?, ?)",
                String.class, original1.confirmationToken(), original2.confirmationToken());
        assertThat(statuses).containsExactlyInAnyOrder("CONFIRMED", "CANCELLED");
    }

    @Test
    void concurrentReschedulesOfTheSameOriginal_exactlyOneSucceeds_loserGetsAppointmentStateChanged_notStaleVersion()
            throws InterruptedException {
        AppointmentResponse original = createOriginal(nextWorkingInstant(5));
        String newHoldToken1 = insertHold(GENERAL_CONSULT_TYPE_ID, nextWorkingInstant(8));
        String newHoldToken2 = insertHold(GENERAL_CONSULT_TYPE_ID, nextWorkingInstant(9));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<RescheduleResponse> future1 = executor.submit(() -> {
            ready.countDown();
            go.await();
            return rescheduleService.reschedule(
                    original.confirmationToken(), newHoldToken1, UUID.randomUUID().toString(), null);
        });
        Future<RescheduleResponse> future2 = executor.submit(() -> {
            ready.countDown();
            go.await();
            return rescheduleService.reschedule(
                    original.confirmationToken(), newHoldToken2, UUID.randomUUID().toString(), null);
        });
        ready.await(5, TimeUnit.SECONDS);
        go.countDown();

        int successCount = 0;
        int stateChangedCount = 0;
        for (Future<RescheduleResponse> future : List.of(future1, future2)) {
            try {
                future.get(10, TimeUnit.SECONDS);
                successCount++;
            } catch (ExecutionException e) {
                if (e.getCause() instanceof AppointmentStateChangedException) {
                    stateChangedCount++;
                } else {
                    throw new AssertionError("Unexpected failure", e.getCause());
                }
            } catch (TimeoutException e) {
                throw new AssertionError("Reschedule timed out", e);
            }
        }
        executor.shutdown();

        assertThat(successCount).isEqualTo(1);
        assertThat(stateChangedCount).isEqualTo(1);
    }

    @Test
    void reschedulingOnlyAppointmentOfDay_toSameProviderSameDay_doesNotTripOwnDailyLimitOrDuplicateCheck() {
        Instant originalStart = nextWorkingInstant(5);
        AppointmentResponse original = createOriginal(originalStart);
        String newHoldToken = insertHold(GENERAL_CONSULT_TYPE_ID, originalStart.plus(3, ChronoUnit.HOURS));

        RescheduleResponse response = rescheduleService.reschedule(
                original.confirmationToken(), newHoldToken, UUID.randomUUID().toString(), null);

        assertThat(response.status()).isEqualTo(Appointment.Status.CONFIRMED);
    }

    @Test
    void rescheduleIntoLessThan24hLeadTime_returnsLeadTimeViolation() {
        AppointmentResponse original = createOriginal(nextWorkingInstant(5));
        String newHoldToken = insertHold(GENERAL_CONSULT_TYPE_ID, Instant.now().plus(10, ChronoUnit.HOURS));

        assertThatThrownBy(() -> rescheduleService.reschedule(
                original.confirmationToken(), newHoldToken, UUID.randomUUID().toString(), null))
                .isInstanceOf(LeadTimeViolationException.class);

        String originalStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM appointments WHERE confirmation_token = ?",
                String.class, original.confirmationToken());
        assertThat(originalStatus).isEqualTo("CONFIRMED");
    }

    @Test
    void rescheduleOfAppointmentInsideCancellationCutoff_returnsCancellationWindowExpired() {
        // A real booking can never be *created* only 2h out (creation itself enforces the 24h
        // lead time) — this state only arises once time has passed since a valid, far-out
        // booking was made, so the original is seeded directly rather than via BookingService.
        String originalToken = seedConfirmedAppointmentDirectly(Instant.now().plus(2, ChronoUnit.HOURS));
        String newHoldToken = insertHold(GENERAL_CONSULT_TYPE_ID, nextWorkingInstant(8));

        assertThatThrownBy(() -> rescheduleService.reschedule(
                originalToken, newHoldToken, UUID.randomUUID().toString(), null))
                .isInstanceOf(CancellationWindowExpiredException.class);
    }

    private String seedConfirmedAppointmentDirectly(Instant start) {
        String token = UUID.randomUUID().toString();
        Instant end = start.plus(30, ChronoUnit.MINUTES);
        String idempotencyKey = UUID.randomUUID().toString();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO appointments (confirmation_token, provider_id, appointment_type_id, "
                            + "patient_full_name, patient_email, patient_phone, start_datetime, end_datetime, "
                            + "status, idempotency_key, request_body_hash) "
                            + "VALUES (?, ?, ?, 'Jordan Rivera', ?, ?, ?, ?, 'CONFIRMED', ?, "
                            + "'0000000000000000000000000000000000000000000000000000000000000000')");
            ps.setString(1, token);
            ps.setLong(2, providerId);
            ps.setLong(3, GENERAL_CONSULT_TYPE_ID);
            ps.setString(4, PATIENT_EMAIL);
            ps.setString(5, PATIENT_PHONE);
            ps.setTimestamp(6, Timestamp.from(start), UTC_CALENDAR);
            ps.setTimestamp(7, Timestamp.from(end), UTC_CALENDAR);
            ps.setString(8, idempotencyKey);
            return ps;
        });
        return token;
    }

    @Test
    void reschedulingPendingAppointment_returnsAppointmentNotReschedulable() {
        String pendingHoldToken = insertHold(NEW_PATIENT_TYPE_ID, nextWorkingInstant(5));
        AppointmentResponse pending = bookingService.createAppointment(
                validRequest(pendingHoldToken), UUID.randomUUID().toString());
        assertThat(pending.status()).isEqualTo(Appointment.Status.PENDING);
        String newHoldToken = insertHold(GENERAL_CONSULT_TYPE_ID, nextWorkingInstant(8));

        assertThatThrownBy(() -> rescheduleService.reschedule(
                pending.confirmationToken(), newHoldToken, UUID.randomUUID().toString(), null))
                .isInstanceOf(AppointmentNotReschedulableException.class);
    }

    @Test
    void exclusionOnlyAppliesToTheOriginal_aGenuineOtherActiveAppointment_stillCountsAgainstTheDailyLimit() {
        // §19 #52's exclusion is scoped to the appointment being rescheduled — a *different*
        // active appointment with the same provider that day must still count. The "other"
        // appointment is seeded directly (two active same-provider appointments in one day is
        // a state the daily limit normally prevents at creation time, §11.6, so it can't be
        // created via the normal booking flow) to prove the reschedule-time check independently.
        Instant day = nextWorkingInstant(5);
        AppointmentResponse original = createOriginal(day);
        seedConfirmedAppointmentDirectly(day.plus(4, ChronoUnit.HOURS));

        String newHoldToken = insertHold(GENERAL_CONSULT_TYPE_ID, day.plus(6, ChronoUnit.HOURS));

        assertThatThrownBy(() -> rescheduleService.reschedule(
                original.confirmationToken(), newHoldToken, UUID.randomUUID().toString(), null))
                .isInstanceOf(PatientDailyLimitExceededException.class);
    }

    @Test
    void idempotentReplay_identicalRequest_returnsOriginalResult_doesNotDoubleCancelOrCreate() {
        AppointmentResponse original = createOriginal(nextWorkingInstant(5));
        String newHoldToken = insertHold(GENERAL_CONSULT_TYPE_ID, nextWorkingInstant(8));
        String idempotencyKey = UUID.randomUUID().toString();

        RescheduleResponse first = rescheduleService.reschedule(
                original.confirmationToken(), newHoldToken, idempotencyKey, "Conflict");
        RescheduleResponse replay = rescheduleService.reschedule(
                original.confirmationToken(), newHoldToken, idempotencyKey, "Conflict");

        assertThat(replay.confirmationToken()).isEqualTo(first.confirmationToken());
        assertThat(replay.previousConfirmationToken()).isEqualTo(original.confirmationToken());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM appointments WHERE idempotency_key = ?", Integer.class, idempotencyKey))
                .isEqualTo(1);

        long originalId = jdbcTemplate.queryForObject(
                "SELECT id FROM appointments WHERE confirmation_token = ?", Long.class, original.confirmationToken());
        long cancelAuditRows = auditLogRepository.findAll().stream()
                .filter(row -> row.getAppointmentId().equals(originalId) && "CANCELLED".equals(row.getNewStatus()))
                .count();
        assertThat(cancelAuditRows).isEqualTo(1);
    }

    private static final String PATIENT_EMAIL = "reschedule-patient@example.com";
    private static final String PATIENT_PHONE = "+14155551234";

    private AppointmentResponse createOriginal(Instant start) {
        return createOriginal(start, PATIENT_EMAIL, PATIENT_PHONE);
    }

    private AppointmentResponse createOriginal(Instant start, String email, String phone) {
        String holdToken = insertHold(GENERAL_CONSULT_TYPE_ID, start);
        return bookingService.createAppointment(
                new CreateAppointmentRequest(holdToken, "Jordan Rivera", email, phone, null),
                UUID.randomUUID().toString());
    }

    private String insertHold(long appointmentTypeId, Instant start) {
        String holdToken = UUID.randomUUID().toString();
        Instant end = start.plus(30, ChronoUnit.MINUTES);
        Instant expiresAt = Instant.now().plusSeconds(300);
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO slot_holds (provider_id, appointment_type_id, start_datetime, end_datetime, hold_token, expires_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?)");
            ps.setLong(1, providerId);
            ps.setLong(2, appointmentTypeId);
            ps.setTimestamp(3, Timestamp.from(start), UTC_CALENDAR);
            ps.setTimestamp(4, Timestamp.from(end), UTC_CALENDAR);
            ps.setString(5, holdToken);
            ps.setTimestamp(6, Timestamp.from(expiresAt), UTC_CALENDAR);
            return ps;
        });
        return holdToken;
    }

    /** A start time at least {@code daysOut} days from now, always inside the seeded all-day WORKING window. */
    private static Instant nextWorkingInstant(int daysOut) {
        return Instant.now().plus(daysOut, ChronoUnit.DAYS).atZone(ZONE).with(LocalTime.of(10, 0)).toInstant();
    }

    private static CreateAppointmentRequest validRequest(String holdToken) {
        return new CreateAppointmentRequest(holdToken, "Jordan Rivera", PATIENT_EMAIL, PATIENT_PHONE, null);
    }
}
