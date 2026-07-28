package com.clinic.booking;

import com.clinic.booking.booking.dto.CreateAppointmentRequest;
import com.clinic.booking.booking.dto.HoldResponse;
import com.clinic.booking.booking.service.BookingService;
import com.clinic.booking.booking.service.HoldService;
import com.clinic.booking.common.exception.SlotHoldExpiredException;
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
import java.util.Calendar;
import java.util.TimeZone;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Milestone 14 — final verification traceability for PRD §18's 12 Acceptance
 * Criteria. Eleven of the twelve already have a dedicated, explicitly-named test
 * elsewhere in the suite (established across Milestones 5, 6, 8, 11, and 12);
 * this class exists only to (a) record that mapping in one place and (b) supply
 * the single test that was genuinely missing, AC-6, rather than duplicate
 * coverage that already exists.
 *
 * <p>Traceability matrix:
 * <pre>
 * AC-1  BookingServiceIT.ac1_generalConsult48hOut_returns201Confirmed
 * AC-2  BookingServiceIT.ac2_newPatient_returns201Pending_andSlotExcludedFromAvailabilityWhilePending
 * AC-3  BookingServiceIT.ac3_sameDay10hLead_returns400LeadTimeViolation
 * AC-4  BookingServiceIT.ac4_concurrentSubmissionsForSameSlot_exactlyOneSucceeds
 * AC-5  AppointmentLookupControllerIT.ac5_flagOff_availabilityIs403_butAppointmentLookupIsStill200
 * AC-6  THIS CLASS — ac6_holdExpiresBeforeSubmission_returns410_andSlotReopensForANewHold (no prior test existed)
 * AC-7  AppointmentLookupControllerIT.ac7_appointmentTwoHoursOut_deleteReturns409WithClinicPhoneNumber
 * AC-8  BookingServiceIT.ac8_fourthSameDayBookingAcrossProviders_returns409PatientDailyLimitExceeded
 * AC-9  BookingServiceIT.ac9_replayedIdempotencyKey_identicalBody_returnsOriginal_mismatchedBody_returns409
 * AC-10 ApprovalTimeoutJobIT.expiresOnlyPendingAppointmentsOlderThanTheApprovalTimeout
 * AC-11 RescheduleServiceIT.ac11_concurrentReschedulesForTheSameNewSlot_exactlyOneSucceeds_loserOriginalRemainsConfirmed
 * AC-12 StaffAuthControllerIT.ac12_fiveFailedLogins_thenCorrectPassword_returns403AccountLocked
 * </pre>
 */
@SpringBootTest
class AcceptanceCriteriaSuiteIT {

    private static final long GENERAL_CONSULT_TYPE_ID = 2L; // requires_approval = FALSE
    private static final ZoneId ZONE = ZoneId.of("America/New_York");
    private static final Calendar UTC_CALENDAR = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

    @Autowired
    private BookingService bookingService;

    @Autowired
    private HoldService holdService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long providerId;

    @BeforeEach
    void seedTestProviderWithAllDaysWorking() {
        String email = "ac-suite-it-" + UUID.randomUUID() + "@example.com";
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
        jdbcTemplate.update("DELETE FROM appointments WHERE provider_id = ?", providerId);
        jdbcTemplate.update("DELETE FROM slot_holds WHERE provider_id = ?", providerId);
        jdbcTemplate.update("DELETE FROM provider_availability_rules WHERE provider_id = ?", providerId);
        jdbcTemplate.update("DELETE FROM providers WHERE id = ?", providerId);
    }

    /**
     * AC-6: a hold that has sat unsubmitted past its 5-minute TTL (§7.8/§12.10)
     * is rejected with {@code 410 SLOT_HOLD_EXPIRED} at submission time. Once the
     * stale row is gone — the hold reaper's job, a separate mechanism (§14
     * Reliability) that this test does not wait on, so its removal is simulated
     * directly — a different patient can acquire a fresh hold for the identical
     * slot, proving it is genuinely free again and not blocked by anything else
     * (an existing appointment, an unrelated hold, ...).
     */
    @Test
    void ac6_holdExpiresBeforeSubmission_returns410_andSlotReopensForANewHold() {
        Instant start = nextWorkingInstant(5);
        String expiredHoldToken = insertExpiredHold(GENERAL_CONSULT_TYPE_ID, start);

        assertThatThrownBy(() -> bookingService.createAppointment(validRequest(expiredHoldToken), UUID.randomUUID().toString()))
                .isInstanceOf(SlotHoldExpiredException.class);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM appointments WHERE provider_id = ?", Integer.class, providerId))
                .isEqualTo(0);

        jdbcTemplate.update("DELETE FROM slot_holds WHERE hold_token = ?", expiredHoldToken);

        HoldResponse secondHold = holdService.createHold(providerId, GENERAL_CONSULT_TYPE_ID, start);
        assertThat(secondHold.holdToken()).isNotBlank();
    }

    private String insertExpiredHold(long appointmentTypeId, Instant start) {
        String holdToken = UUID.randomUUID().toString();
        Instant end = start.plus(30, ChronoUnit.MINUTES);
        Instant alreadyExpired = Instant.now().minusSeconds(60); // took longer than the 5-minute TTL to submit
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO slot_holds (provider_id, appointment_type_id, start_datetime, end_datetime, hold_token, expires_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?)");
            ps.setLong(1, providerId);
            ps.setLong(2, appointmentTypeId);
            ps.setTimestamp(3, Timestamp.from(start), UTC_CALENDAR);
            ps.setTimestamp(4, Timestamp.from(end), UTC_CALENDAR);
            ps.setString(5, holdToken);
            ps.setTimestamp(6, Timestamp.from(alreadyExpired), UTC_CALENDAR);
            return ps;
        });
        return holdToken;
    }

    /** A start time at least {@code daysOut} days from now, always inside the seeded all-day WORKING window. */
    private static Instant nextWorkingInstant(int daysOut) {
        return Instant.now().plus(daysOut, ChronoUnit.DAYS).atZone(ZONE).with(LocalTime.of(10, 0)).toInstant();
    }

    private static CreateAppointmentRequest validRequest(String holdToken) {
        return new CreateAppointmentRequest(
                holdToken, "Jordan Rivera", "jordan@example.com", "+14155551234", null);
    }
}
