package com.clinic.booking.booking.service;

import com.clinic.booking.booking.domain.Appointment;
import com.clinic.booking.booking.dto.AppointmentResponse;
import com.clinic.booking.booking.dto.AvailabilityResponse;
import com.clinic.booking.booking.dto.CreateAppointmentRequest;
import com.clinic.booking.common.exception.IdempotencyKeyReusedMismatchException;
import com.clinic.booking.common.exception.LeadTimeViolationException;
import com.clinic.booking.common.exception.PatientDailyLimitExceededException;
import com.clinic.booking.common.exception.SlotAlreadyBookedException;
import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
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
 * Integration tests for {@link BookingService} (PRD §8.6/§10/§11/§12.9)
 * against the real local MySQL schema — same pattern established in
 * Milestones 1-4 (no Testcontainers dependency introduced). Covers the
 * Milestone 5 validation checklist's named acceptance criteria (AC-1, AC-2,
 * AC-3, AC-4, AC-8, AC-9) plus its three remaining bullets.
 */
@SpringBootTest
class BookingServiceIT {

    private static final long GENERAL_CONSULT_TYPE_ID = 2L; // requires_approval = FALSE
    private static final long NEW_PATIENT_TYPE_ID = 1L; // requires_approval = TRUE
    private static final ZoneId ZONE = ZoneId.of("America/New_York");
    private static final Calendar UTC_CALENDAR = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

    @Autowired
    private BookingService bookingService;

    @Autowired
    private AvailabilityService availabilityService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("bookingSuccessCounter")
    private Counter bookingSuccessCounter;

    private long providerId;

    @BeforeEach
    void seedTestProviderWithAllDaysWorking() {
        String email = "booking-service-it-" + UUID.randomUUID() + "@example.com";
        jdbcTemplate.update(
                "INSERT INTO providers (first_name, last_name, specialty, email, timezone, is_active) "
                        + "VALUES ('Test', 'Provider', 'General Medicine', ?, 'America/New_York', TRUE)",
                email);
        providerId = jdbcTemplate.queryForObject("SELECT id FROM providers WHERE email = ?", Long.class, email);
        // WORKING every day of week, all day, so AC scenarios never accidentally land on a
        // clinic-closed day regardless of which real calendar day the suite runs on.
        for (int dayOfWeek = 0; dayOfWeek <= 6; dayOfWeek++) {
            jdbcTemplate.update(
                    "INSERT INTO provider_availability_rules (provider_id, day_of_week, start_time, end_time, rule_type) "
                            + "VALUES (?, ?, '00:00:00', '23:59:00', 'WORKING')",
                    providerId, dayOfWeek);
        }
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM appointments WHERE provider_id = ?", providerId);
        jdbcTemplate.update("DELETE FROM slot_holds WHERE provider_id = ?", providerId);
        jdbcTemplate.update("DELETE FROM provider_availability_rules WHERE provider_id = ?", providerId);
        jdbcTemplate.update("DELETE FROM providers WHERE id = ?", providerId);
    }

    @Test
    void ac1_generalConsult48hOut_returns201Confirmed() {
        String holdToken = insertHold(GENERAL_CONSULT_TYPE_ID, Instant.now().plus(48, ChronoUnit.HOURS));

        AppointmentResponse response = bookingService.createAppointment(
                validRequest(holdToken), UUID.randomUUID().toString());

        assertThat(response.status()).isEqualTo(Appointment.Status.CONFIRMED);
    }

    @Test
    void successfulBooking_incrementsTheBookingSuccessCounter() {
        // PRD §14: "booking success rate" — delta-based, since the Counter is a
        // process-lifetime singleton shared across every test in this class.
        double before = bookingSuccessCounter.count();
        String holdToken = insertHold(GENERAL_CONSULT_TYPE_ID, Instant.now().plus(48, ChronoUnit.HOURS));

        bookingService.createAppointment(validRequest(holdToken), UUID.randomUUID().toString());

        assertThat(bookingSuccessCounter.count()).isEqualTo(before + 1);
    }

    @Test
    void ac2_newPatient_returns201Pending_andSlotExcludedFromAvailabilityWhilePending() {
        Instant start = nextWorkingInstant(3);
        String holdToken = insertHold(NEW_PATIENT_TYPE_ID, start);

        AppointmentResponse response = bookingService.createAppointment(
                validRequest(holdToken), UUID.randomUUID().toString());

        assertThat(response.status()).isEqualTo(Appointment.Status.PENDING);

        LocalDate date = start.atZone(ZONE).toLocalDate();
        AvailabilityResponse availability = availabilityService.computeAvailableSlots(
                providerId, NEW_PATIENT_TYPE_ID, date);
        assertThat(availability.slots()).doesNotContain(start);
    }

    @Test
    void ac3_sameDay10hLead_returns400LeadTimeViolation() {
        String holdToken = insertHold(GENERAL_CONSULT_TYPE_ID, Instant.now().plus(10, ChronoUnit.HOURS));

        assertThatThrownBy(() -> bookingService.createAppointment(
                validRequest(holdToken), UUID.randomUUID().toString()))
                .isInstanceOf(LeadTimeViolationException.class);
    }

    @Test
    void ac4_concurrentSubmissionsForSameSlot_exactlyOneSucceeds() throws InterruptedException {
        // slot_holds.uq_hold_slot means only one valid hold can ever exist for a given
        // (provider, start) at once — so the realistic version of "two clients racing for
        // the identical slot" is concurrent submissions reading the *same* still-valid hold
        // (nothing locks it on read) and racing to insert into appointments. That is exactly
        // what proves active_slot_key's unique index, not an app-level check, is the arbiter.
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS);
        int attempts = 5;
        String holdToken = insertHold(GENERAL_CONSULT_TYPE_ID, start);

        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        List<Future<AppointmentResponse>> futures = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                go.await();
                return bookingService.createAppointment(validRequest(holdToken), UUID.randomUUID().toString());
            }));
        }
        ready.await(5, TimeUnit.SECONDS);
        go.countDown();

        int successCount = 0;
        int conflictCount = 0;
        for (Future<AppointmentResponse> future : futures) {
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
                throw new AssertionError("Booking creation timed out", e);
            }
        }
        executor.shutdown();

        assertThat(successCount).isEqualTo(1);
        assertThat(conflictCount).isEqualTo(attempts - 1);
    }

    @Test
    void ac8_fourthSameDayBookingAcrossProviders_returns409PatientDailyLimitExceeded() {
        String email = "daily-limit-" + UUID.randomUUID() + "@example.com";
        String phone = "+14155550100";
        Instant day1 = nextWorkingInstant(5);
        List<Long> extraProviderIds = new ArrayList<>();

        try {
            for (int i = 0; i < 4; i++) {
                long bookingProviderId = i == 0 ? providerId : seedAdditionalProvider(extraProviderIds);
                String holdToken = insertHold(bookingProviderId, GENERAL_CONSULT_TYPE_ID, day1.plus(i, ChronoUnit.HOURS));
                CreateAppointmentRequest request =
                        new CreateAppointmentRequest(holdToken, "Jordan Rivera", email, phone, null);

                if (i < 3) {
                    bookingService.createAppointment(request, UUID.randomUUID().toString());
                } else {
                    assertThatThrownBy(() -> bookingService.createAppointment(request, UUID.randomUUID().toString()))
                            .isInstanceOf(PatientDailyLimitExceededException.class);
                }
            }
        } finally {
            for (Long extraProviderId : extraProviderIds) {
                jdbcTemplate.update("DELETE FROM appointments WHERE provider_id = ?", extraProviderId);
                // The 4th (rejected) provider's hold is never consumed, since the booking
                // attempt throws before the insert-and-delete-hold step is ever reached.
                jdbcTemplate.update("DELETE FROM slot_holds WHERE provider_id = ?", extraProviderId);
                jdbcTemplate.update("DELETE FROM provider_availability_rules WHERE provider_id = ?", extraProviderId);
                jdbcTemplate.update("DELETE FROM providers WHERE id = ?", extraProviderId);
            }
        }
    }

    @Test
    void ac9_replayedIdempotencyKey_identicalBody_returnsOriginal_mismatchedBody_returns409() {
        String holdToken = insertHold(GENERAL_CONSULT_TYPE_ID, Instant.now().plus(2, ChronoUnit.DAYS));
        String idempotencyKey = UUID.randomUUID().toString();
        CreateAppointmentRequest request = validRequest(holdToken);

        AppointmentResponse first = bookingService.createAppointment(request, idempotencyKey);
        AppointmentResponse replay = bookingService.createAppointment(request, idempotencyKey);

        assertThat(replay.confirmationToken()).isEqualTo(first.confirmationToken());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM appointments WHERE idempotency_key = ?", Integer.class, idempotencyKey))
                .isEqualTo(1);

        CreateAppointmentRequest mismatched = new CreateAppointmentRequest(
                holdToken, "Someone Else", request.patientEmail(), request.patientPhone(), null);
        assertThatThrownBy(() -> bookingService.createAppointment(mismatched, idempotencyKey))
                .isInstanceOf(IdempotencyKeyReusedMismatchException.class);
    }

    @Test
    void appointmentTypeId_isNeverAcceptedInRequestBody_onlyResolvedFromHold() {
        // CreateAppointmentRequest has no appointmentTypeId field at all — this is a
        // compile-time guarantee, but this test proves the resolved type still matches
        // whatever was stored on the hold at hold-creation time (§7.8/§8.6).
        String holdToken = insertHold(NEW_PATIENT_TYPE_ID, Instant.now().plus(2, ChronoUnit.DAYS));

        AppointmentResponse response = bookingService.createAppointment(
                validRequest(holdToken), UUID.randomUUID().toString());

        Long persistedTypeId = jdbcTemplate.queryForObject(
                "SELECT appointment_type_id FROM appointments WHERE confirmation_token = ?",
                Long.class, response.confirmationToken());
        assertThat(persistedTypeId).isEqualTo(NEW_PATIENT_TYPE_ID);
    }

    @Test
    void notes_htmlIsStrippedServerSide_regardlessOfClientInput() {
        String holdToken = insertHold(GENERAL_CONSULT_TYPE_ID, Instant.now().plus(2, ChronoUnit.DAYS));
        CreateAppointmentRequest request = new CreateAppointmentRequest(
                holdToken, "Jordan Rivera", "jordan@example.com", "+14155551234",
                "<script>alert('xss')</script>Referred by Dr. Lee");

        AppointmentResponse response = bookingService.createAppointment(request, UUID.randomUUID().toString());

        String storedNotes = jdbcTemplate.queryForObject(
                "SELECT notes FROM appointments WHERE confirmation_token = ?", String.class,
                response.confirmationToken());
        assertThat(storedNotes).doesNotContain("<script>").contains("Referred by Dr. Lee");
    }

    private String insertHold(long appointmentTypeId, Instant start) {
        return insertHold(providerId, appointmentTypeId, start);
    }

    private String insertHold(long forProviderId, long appointmentTypeId, Instant start) {
        String holdToken = UUID.randomUUID().toString();
        Instant end = start.plus(30, ChronoUnit.MINUTES);
        Instant expiresAt = Instant.now().plusSeconds(300);
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO slot_holds (provider_id, appointment_type_id, start_datetime, end_datetime, hold_token, expires_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?)");
            ps.setLong(1, forProviderId);
            ps.setLong(2, appointmentTypeId);
            ps.setTimestamp(3, Timestamp.from(start), UTC_CALENDAR);
            ps.setTimestamp(4, Timestamp.from(end), UTC_CALENDAR);
            ps.setString(5, holdToken);
            ps.setTimestamp(6, Timestamp.from(expiresAt), UTC_CALENDAR);
            return ps;
        });
        return holdToken;
    }

    private long seedAdditionalProvider(List<Long> tracking) {
        String email = "booking-service-it-extra-" + UUID.randomUUID() + "@example.com";
        jdbcTemplate.update(
                "INSERT INTO providers (first_name, last_name, specialty, email, timezone, is_active) "
                        + "VALUES ('Extra', 'Provider', 'General Medicine', ?, 'America/New_York', TRUE)",
                email);
        long id = jdbcTemplate.queryForObject("SELECT id FROM providers WHERE email = ?", Long.class, email);
        for (int dayOfWeek = 0; dayOfWeek <= 6; dayOfWeek++) {
            jdbcTemplate.update(
                    "INSERT INTO provider_availability_rules (provider_id, day_of_week, start_time, end_time, rule_type) "
                            + "VALUES (?, ?, '00:00:00', '23:59:00', 'WORKING')",
                    id, dayOfWeek);
        }
        tracking.add(id);
        return id;
    }

    /** A start time at least {@code daysOut} days from now, always inside the seeded all-day WORKING window. */
    private static Instant nextWorkingInstant(int daysOut) {
        return Instant.now().plus(daysOut, ChronoUnit.DAYS).atZone(ZONE).with(LocalTime.of(10, 0)).toInstant();
    }

    private static CreateAppointmentRequest validRequest(String holdToken) {
        return new CreateAppointmentRequest(
                holdToken, "Jordan Rivera", "jordan@example.com", "+14155551234", "First visit");
    }
}
