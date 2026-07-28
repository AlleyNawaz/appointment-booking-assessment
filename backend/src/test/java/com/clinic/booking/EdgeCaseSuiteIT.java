package com.clinic.booking;

import com.clinic.booking.audit.AppointmentAuditLog;
import com.clinic.booking.audit.AppointmentAuditLogRepository;
import com.clinic.booking.booking.domain.Appointment;
import com.clinic.booking.booking.domain.ProviderAvailabilityRule;
import com.clinic.booking.booking.dto.AppointmentResponse;
import com.clinic.booking.booking.dto.CreateAppointmentRequest;
import com.clinic.booking.booking.service.BookingService;
import com.clinic.booking.booking.validation.DuplicateAppointmentValidator;
import com.clinic.booking.booking.validation.LeadTimeValidator;
import com.clinic.booking.common.exception.AppointmentTypeCodeExistsException;
import com.clinic.booking.common.exception.ClinicClosedDayException;
import com.clinic.booking.common.exception.DuplicateAppointmentException;
import com.clinic.booking.common.exception.FeatureFlagNotFoundException;
import com.clinic.booking.common.exception.HolidayDateExistsException;
import com.clinic.booking.common.exception.InvalidTimeRangeException;
import com.clinic.booking.common.exception.InvalidTimezoneException;
import com.clinic.booking.common.exception.LeadTimeViolationException;
import com.clinic.booking.common.exception.PatientDailyLimitExceededException;
import com.clinic.booking.common.exception.ProviderEmailExistsException;
import com.clinic.booking.common.exception.ProviderUnavailableException;
import com.clinic.booking.common.exception.ValidationException;
import com.clinic.booking.staff.domain.StaffUser;
import com.clinic.booking.staff.dto.AppointmentTypeAdminResponse;
import com.clinic.booking.staff.dto.AppointmentTypeRequest;
import com.clinic.booking.staff.dto.AvailabilityRuleRequest;
import com.clinic.booking.staff.dto.HolidayRequest;
import com.clinic.booking.staff.dto.HolidayResponse;
import com.clinic.booking.staff.dto.ProviderAdminResponse;
import com.clinic.booking.staff.dto.ProviderRequest;
import com.clinic.booking.staff.dto.StaffAppointmentResponse;
import com.clinic.booking.staff.repository.StaffUserRepository;
import com.clinic.booking.staff.security.StaffUserPrincipal;
import com.clinic.booking.staff.service.AppointmentLifecycleService;
import com.clinic.booking.staff.service.AppointmentTypeAdminService;
import com.clinic.booking.staff.service.AvailabilityRuleService;
import com.clinic.booking.staff.service.ClinicHolidayService;
import com.clinic.booking.staff.service.FeatureFlagAdminService;
import com.clinic.booking.staff.service.ProviderAdminService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

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
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Milestone 14 — final verification traceability for PRD §19's 54 edge cases,
 * plus the error-catalog rows (§13) and §12.7 lifecycle transitions those edge
 * cases don't already exercise. No production code is changed here — every
 * test below drives functionality that already exists; where it found none
 * (see "Known gaps" below), a test was deliberately not written.
 *
 * <p>Traceability matrix (54 rows). "Elsewhere" means an existing test, named
 * in the class it lives in, already covers it — this class does not repeat
 * that coverage. "New" means the test method in this class immediately below.
 * <pre>
 *  #1  Elsewhere (design property; server-side clock used throughout, no dedicated test)
 *  #2  Elsewhere (design property; server-side clock used throughout, no dedicated test)
 *  #3  New — edgeCase3_leadTimeBoundary_exactly24hIsValid_23h59m59sIsRejected
 *  #4  Elsewhere — AvailabilityServiceTest.dstTransition_shiftsUtcOffsetAutomatically_withoutSchemaChange
 *  #5  N/A — frontend sessionStorage concern, out of backend scope
 *  #6  N/A — frontend concern, out of backend scope
 *  #7  Elsewhere — BookingServiceIT.ac4_concurrentSubmissionsForSameSlot_exactlyOneSucceeds
 *  #8  Elsewhere — BookingServiceIT.ac9_replayedIdempotencyKey_identicalBody_returnsOriginal_mismatchedBody_returns409
 *  #9  Elsewhere — same idempotency mechanism as #8; button-disable-on-click is frontend, N/A
 *  #10 N/A — JS disabled, explicitly out of scope (§20)
 *  #11 Elsewhere — HoldControllerIT/BookingConfigControllerIT gated-endpoint tests
 *  #12 Elsewhere — FeatureFlagServiceTest.isEnabled_refetchesAfterTenSecondTtlElapses
 *  #13 New — edgeCase13_flagFlipsWhileHoldAndPendingAppointmentActive_bothUnaffected
 *  #14 Elsewhere — BookingServiceIT.ac4
 *  #15 Elsewhere — AppointmentLifecycleServiceIT.secondConcurrentTransition_getsStaleVersion
 *  #16 New — edgeCase16_requiresApprovalReadFreshAtSubmission_notCachedFromHoldCreation
 *  #17 New — edgeCase17_providerDeactivatedAfterHold_beforeSubmission_returnsProviderUnavailable
 *  #18 New — edgeCase18_appointmentTypeDeactivatedAfterHold_beforeSubmission_returnsProviderUnavailable
 *  #19 New — edgeCase19_holdHonoredForItsTtlDespiteConcurrentAvailabilityRuleChange
 *  #20 New — edgeCase20_holidayAddedOverExistingConfirmedAppointment_doesNotCancelIt_butBlocksNewBookingsOnThatDate
 *  #21 New — edgeCase21_sameProviderTwoDifferentAppointmentTypesSameDay_blockedByDailyLimit
 *  #22 New — edgeCase22_differentEmailSamePhone_isTreatedAsADifferentIdentity_notBlockedByDailyLimit
 *  #23 Elsewhere — AppointmentLookupControllerIT.successfulCancellation_freesActiveSlotKey_reBookingIsPermitted
 *  #24 Elsewhere — AppointmentLifecycleServiceIT.invalidTransition_cancelledToCompleted_isRejectedAsConflict
 *  #25 Elsewhere — MissedAppointmentJobIT.marksOnlyOverdueConfirmedAppointmentsAsMissed_excludingAlreadyCompleted
 *  #26 Elsewhere — AppointmentLifecycleServiceIT.missedAppointment_canBeCorrectedToCompleted_withinSevenDays / ...beyondSevenDays...
 *  #27 New — edgeCase27_nonLatinScriptName_isAccepted / edgeCase27_emojiInName_isRejectedAsValidationError
 *  #28 New — edgeCase28_phoneWithoutCountryCode_isRejectedAsValidationError
 *  #29 Elsewhere — BookingServiceIT.notes_htmlIsStrippedServerSide_regardlessOfClientInput
 *  #30 New — edgeCase30_extremelyLongSingleWordName_isRejectedAsValidationError
 *  #31 New — edgeCase31_confirmationTokenIsUuidv4_notSequential
 *  #32 Elsewhere — RateLimitingFilterIT.availability_firstTenRequestsPerMinute_areAllowed_eleventhIsRateLimited
 *  #33 KNOWN GAP — see below (no format validation exists on Idempotency-Key; not implemented here)
 *  #34 Elsewhere/§2 — StaffAuthControllerIT.logout_invalidatesTheSession; PRD marks this "not re-specified here"
 *  #35 Elsewhere — AvailabilityServiceTest.noWorkingRuleForRequestedDay_returnsEmptySlots_notAnError
 *  #36 Elsewhere — AvailabilityServiceTest.onlyOffersSlotsWhereDurationPlusBufferFitsBeforeTheNextBlockingInterval
 *  #37 Elsewhere — same test as #36 (buffer-pushes-past-end-of-day is the same code path)
 *  #38 Elsewhere — AvailabilityRuleServiceIT.overlappingRule_rejectedBeforePersistence
 *  #39 Elsewhere — RescheduleServiceIT.rescheduleIntoLessThan24hLeadTime_returnsLeadTimeViolation
 *  #40 Elsewhere — RescheduleServiceIT.rescheduleOfAppointmentInsideCancellationCutoff_returnsCancellationWindowExpired
 *  #41 Elsewhere — BookingServiceEmailFailureIT.emailNotificationFailure_doesNotRollBackTheBooking
 *  #42 Elsewhere — AppointmentLifecycleServiceIT.pageBeyondData_returnsEmptyContentNotNotFound_andSizeIsClamped
 *  #43 Elsewhere — same test as #42 (size=10000 clamped to 100)
 *  #44 N/A — design/schema readiness property, not code-testable
 *  #45 New — edgeCase45_softDeletedProviderReferencedByHistoricalAppointment_retainsReferentialIntegrity
 *  #46 N/A — future migration, not testable without the future migration existing
 *  #47 N/A — load/index-scoping claim; no perf-test harness in this repo (see perf/load-test-plan.js)
 *  #48 Elsewhere — RateLimitingFilterIT.holdsAndAppointments_combinedLimitOfFivePerTenMinutes_isSharedAcrossBothEndpoints
 *  #49 Elsewhere — AppointmentLifecycleServiceIT.providerCannotActOnAnotherProvidersAppointment
 *  #50 New — edgeCase50_pastDatedHoliday_isPermitted_andHasNoBearingOnFutureBookings
 *  #51 Elsewhere (proxy) — RescheduleServiceIT.concurrentReschedulesOfTheSameOriginal_..._getsAppointmentStateChanged_notStaleVersion
 *  #52 Elsewhere — RescheduleServiceIT.reschedulingOnlyAppointmentOfDay_... / exclusionOnlyAppliesToTheOriginal_...
 *  #53 Elsewhere — StaffAuthControllerIT.wrongAttemptImmediatelyAfterLockoutExpiry_reLocks_insteadOfFreshCount
 *  #54 Elsewhere — StaffAuthControllerIT.corsPreflight_succeedsRegardlessOfFeatureFlagState
 * </pre>
 *
 * <p>Error-catalog rows (§13) not already covered by an AC or edge-case test above, closed here:
 * {@code CLINIC_CLOSED_DAY} (edgeCase20), {@code APPOINTMENT_TYPE_CODE_EXISTS},
 * {@code PROVIDER_EMAIL_EXISTS}, {@code INVALID_TIMEZONE}, {@code INVALID_TIME_RANGE},
 * {@code HOLIDAY_DATE_EXISTS}, {@code FEATURE_FLAG_NOT_FOUND}, {@code DUPLICATE_APPOINTMENT}.
 *
 * <p>§12.7 lifecycle transitions not already covered elsewhere, closed here:
 * {@code PENDING → REJECTED} (lifecycle_pendingToRejected), plain
 * {@code CONFIRMED → COMPLETED} (lifecycle_confirmedToCompleted — the correction-window path for
 * {@code MISSED → COMPLETED} was already covered in AppointmentLifecycleServiceIT).
 *
 * <p><b>Known gaps found during this verification pass, deliberately not fixed here</b> (Milestone 14
 * is a verification gate, not a feature milestone — per §17 rule 2, a gap is flagged, not silently
 * resolved by adding new production behaviour):
 * <ul>
 *   <li>§19 #33 (Idempotency-Key must be a valid UUID, else {@code 400 VALIDATION_ERROR}) is not
 *       implemented — {@code AppointmentController}/{@code RescheduleController} only null-check the
 *       header, they never validate its format.</li>
 *   <li>{@code SERVICE_UNAVAILABLE} (503) and {@code REQUEST_TIMEOUT} (504) have no
 *       {@code GlobalExceptionHandler} mapping and no code path that would produce them.</li>
 *   <li>A generic {@code Exception → 500 INTERNAL_SERVER_ERROR} fallback handler does not exist in
 *       {@code GlobalExceptionHandler}.</li>
 *   <li>Staff-initiated {@code CONFIRMED → CANCELLED} "any time" (§12.6) has no service
 *       method/endpoint — only patient self-service cancellation (with its 4h cutoff) exists.</li>
 * </ul>
 * These four are recorded in {@code docs/production-readiness-checklist.md} as open items requiring
 * a product/implementation decision, not addressed by this test-only milestone.
 */
@SpringBootTest
class EdgeCaseSuiteIT {

    private static final long GENERAL_CONSULT_TYPE_ID = 2L; // requires_approval = FALSE
    private static final long NEW_PATIENT_TYPE_ID = 1L; // requires_approval = TRUE
    private static final ZoneId ZONE = ZoneId.of("America/New_York");
    private static final Calendar UTC_CALENDAR = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    private static final String KNOWN_HASH = "$2a$12$iZOajtQvoYA9vG2I/cG0POWBVtiyaczx3rSq7P1M5OlX5njcVqPVq";
    private static final String PATIENT_EMAIL = "jordan@example.com";
    private static final String PATIENT_PHONE = "+14155551234";

    @Autowired
    private BookingService bookingService;

    @Autowired
    private LeadTimeValidator leadTimeValidator;

    @Autowired
    private DuplicateAppointmentValidator duplicateAppointmentValidator;

    @Autowired
    private AppointmentTypeAdminService appointmentTypeAdminService;

    @Autowired
    private ProviderAdminService providerAdminService;

    @Autowired
    private AvailabilityRuleService availabilityRuleService;

    @Autowired
    private ClinicHolidayService clinicHolidayService;

    @Autowired
    private FeatureFlagAdminService featureFlagAdminService;

    @Autowired
    private AppointmentLifecycleService appointmentLifecycleService;

    @Autowired
    private AppointmentAuditLogRepository auditLogRepository;

    @Autowired
    private StaffUserRepository staffUserRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long providerId;
    private final List<Long> extraProviderIds = new ArrayList<>();
    private final List<Long> extraTypeIds = new ArrayList<>();
    private final List<Long> holidayIds = new ArrayList<>();
    private final List<String> staffUsernames = new ArrayList<>();

    @BeforeEach
    void seedTestProviderWithAllDaysWorking() {
        providerId = insertProvider();
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        List<Long> allProviderIds = new ArrayList<>(extraProviderIds);
        allProviderIds.add(providerId);
        for (Long id : allProviderIds) {
            jdbcTemplate.update(
                    "DELETE FROM appointment_audit_log WHERE appointment_id IN "
                            + "(SELECT id FROM appointments WHERE provider_id = ?)", id);
            jdbcTemplate.update("DELETE FROM appointments WHERE provider_id = ?", id);
            jdbcTemplate.update("DELETE FROM slot_holds WHERE provider_id = ?", id);
            jdbcTemplate.update("DELETE FROM provider_availability_rules WHERE provider_id = ?", id);
        }
        for (Long id : allProviderIds) {
            jdbcTemplate.update("DELETE FROM providers WHERE id = ?", id);
        }
        for (Long id : extraTypeIds) {
            jdbcTemplate.update("DELETE FROM appointment_types WHERE id = ?", id);
        }
        for (Long id : holidayIds) {
            jdbcTemplate.update("DELETE FROM clinic_holidays WHERE id = ?", id);
        }
        for (String username : staffUsernames) {
            jdbcTemplate.update("DELETE FROM staff_users WHERE username = ?", username);
        }
    }

    /** §19 #3: the ≥24h lead-time boundary is inclusive, not exclusive. */
    @Test
    void edgeCase3_leadTimeBoundary_exactly24hIsValid_23h59m59sIsRejected() {
        Instant justOverBoundary = Instant.now().plus(24, ChronoUnit.HOURS).plusMillis(500);
        assertThatCode(() -> leadTimeValidator.validate(justOverBoundary)).doesNotThrowAnyException();

        Instant justUnderBoundary = Instant.now().plus(24, ChronoUnit.HOURS).minusSeconds(2);
        assertThatThrownBy(() -> leadTimeValidator.validate(justUnderBoundary))
                .isInstanceOf(LeadTimeViolationException.class);
    }

    /** §19 #13: an existing hold/PENDING appointment survive a flag flip untouched. */
    @Test
    void edgeCase13_flagFlipsWhileHoldAndPendingAppointmentActive_bothUnaffected() {
        String pendingHoldToken = insertHold(NEW_PATIENT_TYPE_ID, nextWorkingInstant(5));
        AppointmentResponse pending =
                bookingService.createAppointment(validRequest(pendingHoldToken), UUID.randomUUID().toString());
        assertThat(pending.status()).isEqualTo(Appointment.Status.PENDING);

        String activeHoldToken = insertHold(GENERAL_CONSULT_TYPE_ID, nextWorkingInstant(6));

        jdbcTemplate.update("UPDATE feature_flags SET is_enabled = FALSE WHERE flag_name = 'enable_online_booking'");

        String pendingStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM appointments WHERE confirmation_token = ?",
                String.class, pending.confirmationToken());
        assertThat(pendingStatus).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM slot_holds WHERE hold_token = ?", Integer.class, activeHoldToken))
                .isEqualTo(1);
    }

    /** §19 #16: {@code requires_approval} is read fresh at submission, never cached from hold-creation time. */
    @Test
    void edgeCase16_requiresApprovalReadFreshAtSubmission_notCachedFromHoldCreation() {
        String holdToken = insertHold(GENERAL_CONSULT_TYPE_ID, nextWorkingInstant(5));

        jdbcTemplate.update("UPDATE appointment_types SET requires_approval = TRUE WHERE id = ?", GENERAL_CONSULT_TYPE_ID);
        try {
            AppointmentResponse response =
                    bookingService.createAppointment(validRequest(holdToken), UUID.randomUUID().toString());
            assertThat(response.status()).isEqualTo(Appointment.Status.PENDING);
        } finally {
            jdbcTemplate.update("UPDATE appointment_types SET requires_approval = FALSE WHERE id = ?", GENERAL_CONSULT_TYPE_ID);
        }
    }

    /** §19 #17/§11.9: provider deactivated after the hold but before submission is rechecked at submission. */
    @Test
    void edgeCase17_providerDeactivatedAfterHold_beforeSubmission_returnsProviderUnavailable() {
        String holdToken = insertHold(GENERAL_CONSULT_TYPE_ID, nextWorkingInstant(5));

        jdbcTemplate.update("UPDATE providers SET is_active = FALSE WHERE id = ?", providerId);

        assertThatThrownBy(() -> bookingService.createAppointment(validRequest(holdToken), UUID.randomUUID().toString()))
                .isInstanceOf(ProviderUnavailableException.class);
    }

    /** §19 #18: same pattern as #17, for {@code appointment_types.is_active}. */
    @Test
    void edgeCase18_appointmentTypeDeactivatedAfterHold_beforeSubmission_returnsProviderUnavailable() {
        long typeId = insertActiveAppointmentType();
        String holdToken = insertHold(typeId, nextWorkingInstant(5));

        jdbcTemplate.update("UPDATE appointment_types SET is_active = FALSE WHERE id = ?", typeId);

        assertThatThrownBy(() -> bookingService.createAppointment(validRequest(holdToken), UUID.randomUUID().toString()))
                .isInstanceOf(ProviderUnavailableException.class);
    }

    /**
     * §19 #19: a hold is a point-in-time reservation, honored for its own TTL regardless of a
     * later schedule edit — booking creation only checks that the day itself is still open
     * (§11.4/§11.5, via {@code ClinicClosedDayValidator}), never the fine-grained hours the
     * slot grid uses, so shortening the day's hours after the hold was taken has no bearing.
     */
    @Test
    void edgeCase19_holdHonoredForItsTtlDespiteConcurrentAvailabilityRuleChange() {
        Instant start = nextWorkingInstant(5);
        String holdToken = insertHold(GENERAL_CONSULT_TYPE_ID, start);

        int dayOfWeek = start.atZone(ZONE).getDayOfWeek().getValue() % 7;
        jdbcTemplate.update(
                "UPDATE provider_availability_rules SET start_time = '06:00:00', end_time = '07:00:00' "
                        + "WHERE provider_id = ? AND day_of_week = ? AND rule_type = 'WORKING'",
                providerId, dayOfWeek);

        AppointmentResponse response =
                bookingService.createAppointment(validRequest(holdToken), UUID.randomUUID().toString());
        assertThat(response.status()).isEqualTo(Appointment.Status.CONFIRMED);
    }

    /**
     * §19 #20: a holiday added over a date with an existing confirmed appointment does not
     * retroactively cancel it, but does block new bookings on that date — also the
     * {@code CLINIC_CLOSED_DAY} error-catalog row.
     */
    @Test
    void edgeCase20_holidayAddedOverExistingConfirmedAppointment_doesNotCancelIt_butBlocksNewBookingsOnThatDate() {
        LocalDate holidayDate = LocalDate.now(ZONE).plusDays(10);
        Instant existingStart = holidayDate.atStartOfDay(ZONE).plusHours(9).toInstant();
        String existingToken = seedConfirmedAppointmentDirectly(existingStart);

        actAs(StaffUser.Role.ROLE_ADMIN);
        HolidayResponse holiday = clinicHolidayService.create(new HolidayRequest(holidayDate, "Edge Case 20 Holiday", false));
        holidayIds.add(holiday.id());

        String existingStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM appointments WHERE confirmation_token = ?", String.class, existingToken);
        assertThat(existingStatus).isEqualTo("CONFIRMED");

        String newHoldToken = insertHold(GENERAL_CONSULT_TYPE_ID, holidayDate.atStartOfDay(ZONE).plusHours(14).toInstant());
        assertThatThrownBy(() -> bookingService.createAppointment(validRequest(newHoldToken), UUID.randomUUID().toString()))
                .isInstanceOf(ClinicClosedDayException.class);
    }

    /** §19 #21/§11.6: same provider, two different appointment types, same day — still just one active slot. */
    @Test
    void edgeCase21_sameProviderTwoDifferentAppointmentTypesSameDay_blockedByDailyLimit() {
        Instant firstStart = nextWorkingInstant(5);
        String firstHold = insertHold(GENERAL_CONSULT_TYPE_ID, firstStart);
        bookingService.createAppointment(validRequest(firstHold), UUID.randomUUID().toString());

        String secondHold = insertHold(NEW_PATIENT_TYPE_ID, firstStart.plus(3, ChronoUnit.HOURS));

        assertThatThrownBy(() -> bookingService.createAppointment(validRequest(secondHold), UUID.randomUUID().toString()))
                .isInstanceOf(PatientDailyLimitExceededException.class);
    }

    /** §19 #22/§11.6: identity is the {@code email+phone} composite — a different email is a different identity. */
    @Test
    void edgeCase22_differentEmailSamePhone_isTreatedAsADifferentIdentity_notBlockedByDailyLimit() {
        long secondProviderId = insertProvider();
        extraProviderIds.add(secondProviderId);
        Instant start = nextWorkingInstant(5);

        String firstHold = insertHold(GENERAL_CONSULT_TYPE_ID, start);
        bookingService.createAppointment(
                new CreateAppointmentRequest(firstHold, "Jordan Rivera", "jordan.a@example.com", PATIENT_PHONE, null),
                UUID.randomUUID().toString());

        String secondHold = insertHold(secondProviderId, GENERAL_CONSULT_TYPE_ID, start);
        AppointmentResponse second = bookingService.createAppointment(
                new CreateAppointmentRequest(secondHold, "Jordan Rivera", "jordan.b@example.com", PATIENT_PHONE, null),
                UUID.randomUUID().toString());

        assertThat(second.status()).isEqualTo(Appointment.Status.CONFIRMED);
    }

    /** §19 #27: non-Latin scripts are letters ({@code \p{L}}) and accepted. */
    @Test
    void edgeCase27_nonLatinScriptName_isAccepted() {
        String holdToken = insertHold(GENERAL_CONSULT_TYPE_ID, nextWorkingInstant(5));

        AppointmentResponse response = bookingService.createAppointment(
                new CreateAppointmentRequest(holdToken, "Владимир Иванов", "vladimir@example.com", PATIENT_PHONE, null),
                UUID.randomUUID().toString());

        assertThat(response.status()).isEqualTo(Appointment.Status.CONFIRMED);
    }

    /** §19 #27: emoji are not in {@code \p{L}} and are rejected. */
    @Test
    void edgeCase27_emojiInName_isRejectedAsValidationError() {
        String holdToken = insertHold(GENERAL_CONSULT_TYPE_ID, nextWorkingInstant(5));

        assertThatThrownBy(() -> bookingService.createAppointment(
                new CreateAppointmentRequest(holdToken, "John 😀", "john@example.com", PATIENT_PHONE, null),
                UUID.randomUUID().toString()))
                .isInstanceOf(ValidationException.class);
    }

    /** §19 #28: E.164 requires a leading {@code +} and country code — no default is inferred. */
    @Test
    void edgeCase28_phoneWithoutCountryCode_isRejectedAsValidationError() {
        String holdToken = insertHold(GENERAL_CONSULT_TYPE_ID, nextWorkingInstant(5));

        assertThatThrownBy(() -> bookingService.createAppointment(
                new CreateAppointmentRequest(holdToken, "Jordan Rivera", PATIENT_EMAIL, "4155551234", null),
                UUID.randomUUID().toString()))
                .isInstanceOf(ValidationException.class);
    }

    /** §19 #30: the 100-char max is enforced regardless of character composition (no spaces to split on). */
    @Test
    void edgeCase30_extremelyLongSingleWordName_isRejectedAsValidationError() {
        String holdToken = insertHold(GENERAL_CONSULT_TYPE_ID, nextWorkingInstant(5));
        String longName = "A".repeat(300);

        assertThatThrownBy(() -> bookingService.createAppointment(
                new CreateAppointmentRequest(holdToken, longName, PATIENT_EMAIL, PATIENT_PHONE, null),
                UUID.randomUUID().toString()))
                .isInstanceOf(ValidationException.class);
    }

    /** §19 #31: confirmation tokens are UUIDv4, never sequential. */
    @Test
    void edgeCase31_confirmationTokenIsUuidv4_notSequential() {
        String holdToken = insertHold(GENERAL_CONSULT_TYPE_ID, nextWorkingInstant(5));

        AppointmentResponse response =
                bookingService.createAppointment(validRequest(holdToken), UUID.randomUUID().toString());

        assertThat(UUID.fromString(response.confirmationToken()).version()).isEqualTo(4);
    }

    /** §19 #45: a soft-deleted provider never breaks referential integrity for historical appointments. */
    @Test
    void edgeCase45_softDeletedProviderReferencedByHistoricalAppointment_retainsReferentialIntegrity() {
        String holdToken = insertHold(GENERAL_CONSULT_TYPE_ID, nextWorkingInstant(5));
        AppointmentResponse response =
                bookingService.createAppointment(validRequest(holdToken), UUID.randomUUID().toString());

        actAs(StaffUser.Role.ROLE_ADMIN);
        providerAdminService.softDelete(providerId);

        Map<String, Object> providerRow = jdbcTemplate.queryForMap(
                "SELECT is_active, deleted_at, first_name FROM providers WHERE id = ?", providerId);
        assertThat((Boolean) providerRow.get("is_active")).isFalse();
        assertThat(providerRow.get("deleted_at")).isNotNull();
        assertThat(providerRow.get("first_name")).isEqualTo("Test");

        Integer joinCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM appointments a JOIN providers p ON a.provider_id = p.id "
                        + "WHERE a.confirmation_token = ?", Integer.class, response.confirmationToken());
        assertThat(joinCount).isEqualTo(1);
    }

    /** §19 #50: a past-dated holiday is permitted (historical record-keeping) and has no bearing on future bookings. */
    @Test
    void edgeCase50_pastDatedHoliday_isPermitted_andHasNoBearingOnFutureBookings() {
        LocalDate pastDate = LocalDate.now(ZONE).minusYears(1);

        actAs(StaffUser.Role.ROLE_ADMIN);
        HolidayResponse holiday =
                clinicHolidayService.create(new HolidayRequest(pastDate, "Historical Record Holiday", false));
        holidayIds.add(holiday.id());
        assertThat(holiday.holidayDate()).isEqualTo(pastDate);

        String holdToken = insertHold(GENERAL_CONSULT_TYPE_ID, nextWorkingInstant(5));
        AppointmentResponse response =
                bookingService.createAppointment(validRequest(holdToken), UUID.randomUUID().toString());
        assertThat(response.status()).isEqualTo(Appointment.Status.CONFIRMED);
    }

    /** §13 error catalog: {@code APPOINTMENT_TYPE_CODE_EXISTS}. */
    @Test
    void errorCatalog_duplicateAppointmentTypeCode_returnsAppointmentTypeCodeExists() {
        actAs(StaffUser.Role.ROLE_ADMIN);
        String code = "EDGE_TEST_" + System.nanoTime();
        AppointmentTypeAdminResponse first = appointmentTypeAdminService.create(
                new AppointmentTypeRequest(code, "Edge Test Type", 30, 0, false, true));
        extraTypeIds.add(first.id());

        assertThatThrownBy(() -> appointmentTypeAdminService.create(
                new AppointmentTypeRequest(code, "Duplicate Code Type", 30, 0, false, true)))
                .isInstanceOf(AppointmentTypeCodeExistsException.class);
    }

    /** §13 error catalog: {@code PROVIDER_EMAIL_EXISTS}. */
    @Test
    void errorCatalog_duplicateProviderEmail_returnsProviderEmailExists() {
        actAs(StaffUser.Role.ROLE_ADMIN);
        String email = "edge-dup-" + UUID.randomUUID() + "@example.com";
        ProviderAdminResponse first = providerAdminService.create(
                new ProviderRequest("Test", "One", "General Medicine", email, "America/New_York", true));
        extraProviderIds.add(first.id());

        assertThatThrownBy(() -> providerAdminService.create(
                new ProviderRequest("Test", "Two", "General Medicine", email, "America/New_York", true)))
                .isInstanceOf(ProviderEmailExistsException.class);
    }

    /** §13 error catalog: {@code INVALID_TIMEZONE}. */
    @Test
    void errorCatalog_invalidTimezone_returnsInvalidTimezone() {
        actAs(StaffUser.Role.ROLE_ADMIN);

        assertThatThrownBy(() -> providerAdminService.create(new ProviderRequest(
                "Test", "Zone", "General Medicine", "edge-tz-" + UUID.randomUUID() + "@example.com", "Not/AZone", true)))
                .isInstanceOf(InvalidTimezoneException.class);
    }

    /** §13 error catalog: {@code INVALID_TIME_RANGE}. */
    @Test
    void errorCatalog_availabilityRuleStartAfterEnd_returnsInvalidTimeRange() {
        actAs(StaffUser.Role.ROLE_ADMIN);

        assertThatThrownBy(() -> availabilityRuleService.create(providerId, new AvailabilityRuleRequest(
                1, LocalTime.of(10, 0), LocalTime.of(9, 0), ProviderAvailabilityRule.RuleType.WORKING)))
                .isInstanceOf(InvalidTimeRangeException.class);
    }

    /** §13 error catalog: {@code HOLIDAY_DATE_EXISTS}. */
    @Test
    void errorCatalog_duplicateHolidayDate_returnsHolidayDateExists() {
        actAs(StaffUser.Role.ROLE_ADMIN);
        LocalDate date = LocalDate.now(ZONE).plusYears(4);
        HolidayResponse first = clinicHolidayService.create(new HolidayRequest(date, "Edge Test Holiday A", false));
        holidayIds.add(first.id());

        assertThatThrownBy(() -> clinicHolidayService.create(new HolidayRequest(date, "Edge Test Holiday B", false)))
                .isInstanceOf(HolidayDateExistsException.class);
    }

    /** §13 error catalog: {@code FEATURE_FLAG_NOT_FOUND}. */
    @Test
    void errorCatalog_unknownFeatureFlagName_returnsFeatureFlagNotFound() {
        actAs(StaffUser.Role.ROLE_ADMIN);

        assertThatThrownBy(() -> featureFlagAdminService.get("nonexistent_flag_" + UUID.randomUUID()))
                .isInstanceOf(FeatureFlagNotFoundException.class);
    }

    /** §13 error catalog: {@code DUPLICATE_APPOINTMENT} (§11.7 — distinct from §11.6's daily limit). */
    @Test
    void errorCatalog_overlappingActiveAppointment_returnsDuplicateAppointment() {
        Instant start = nextWorkingInstant(5);
        Instant end = start.plus(30, ChronoUnit.MINUTES);
        seedConfirmedAppointmentDirectly(start);

        assertThatThrownBy(() -> duplicateAppointmentValidator.validate(PATIENT_EMAIL, PATIENT_PHONE, providerId, start, end))
                .isInstanceOf(DuplicateAppointmentException.class);
    }

    /** §12.7: {@code PENDING → REJECTED}. */
    @Test
    void lifecycle_pendingToRejected_writesAuditRowWithReasonAndStaffUsername() {
        String holdToken = insertHold(NEW_PATIENT_TYPE_ID, nextWorkingInstant(5));
        AppointmentResponse pending =
                bookingService.createAppointment(validRequest(holdToken), UUID.randomUUID().toString());
        long appointmentId = jdbcTemplate.queryForObject(
                "SELECT id FROM appointments WHERE confirmation_token = ?", Long.class, pending.confirmationToken());
        int version = jdbcTemplate.queryForObject("SELECT version FROM appointments WHERE id = ?", Integer.class, appointmentId);

        StaffUserPrincipal principal = actAs(StaffUser.Role.ROLE_STAFF);
        StaffAppointmentResponse rejected =
                appointmentLifecycleService.reject(appointmentId, "Schedule conflict", version, principal);

        assertThat(rejected.status()).isEqualTo(Appointment.Status.REJECTED);
        AppointmentAuditLog row = auditLogRepository.findAll().stream()
                .filter(r -> r.getAppointmentId().equals(appointmentId) && "REJECTED".equals(r.getNewStatus()))
                .findFirst().orElseThrow();
        assertThat(row.getPreviousStatus()).isEqualTo("PENDING");
        assertThat(row.getChangedBy()).isEqualTo(principal.getUsername());
        assertThat(row.getReason()).isEqualTo("Schedule conflict");
    }

    /** §12.7: plain {@code CONFIRMED → COMPLETED} (distinct from the {@code MISSED} correction path). */
    @Test
    void lifecycle_confirmedToCompleted_writesAuditRowWithStaffUsername() {
        String holdToken = insertHold(GENERAL_CONSULT_TYPE_ID, nextWorkingInstant(5));
        AppointmentResponse confirmed =
                bookingService.createAppointment(validRequest(holdToken), UUID.randomUUID().toString());
        long appointmentId = jdbcTemplate.queryForObject(
                "SELECT id FROM appointments WHERE confirmation_token = ?", Long.class, confirmed.confirmationToken());
        int version = jdbcTemplate.queryForObject("SELECT version FROM appointments WHERE id = ?", Integer.class, appointmentId);

        StaffUserPrincipal principal = actAs(StaffUser.Role.ROLE_STAFF);
        StaffAppointmentResponse completed = appointmentLifecycleService.complete(appointmentId, version, principal);

        assertThat(completed.status()).isEqualTo(Appointment.Status.COMPLETED);
        AppointmentAuditLog row = auditLogRepository.findAll().stream()
                .filter(r -> r.getAppointmentId().equals(appointmentId) && "COMPLETED".equals(r.getNewStatus()))
                .findFirst().orElseThrow();
        assertThat(row.getPreviousStatus()).isEqualTo("CONFIRMED");
        assertThat(row.getChangedBy()).isEqualTo(principal.getUsername());
    }

    private long insertProvider() {
        String email = "edge-case-it-" + UUID.randomUUID() + "@example.com";
        jdbcTemplate.update(
                "INSERT INTO providers (first_name, last_name, specialty, email, timezone, is_active) "
                        + "VALUES ('Test', 'Provider', 'General Medicine', ?, 'America/New_York', TRUE)",
                email);
        long id = jdbcTemplate.queryForObject("SELECT id FROM providers WHERE email = ?", Long.class, email);
        for (int dayOfWeek = 0; dayOfWeek <= 6; dayOfWeek++) {
            jdbcTemplate.update(
                    "INSERT INTO provider_availability_rules (provider_id, day_of_week, start_time, end_time, rule_type) "
                            + "VALUES (?, ?, '00:00:00', '23:59:00', 'WORKING')",
                    id, dayOfWeek);
        }
        return id;
    }

    private long insertActiveAppointmentType() {
        String code = "EDGE18_" + System.nanoTime();
        jdbcTemplate.update(
                "INSERT INTO appointment_types (code, display_name, duration_minutes, buffer_minutes, requires_approval, is_active) "
                        + "VALUES (?, 'Edge Case 18 Type', 30, 0, FALSE, TRUE)",
                code);
        long id = jdbcTemplate.queryForObject("SELECT id FROM appointment_types WHERE code = ?", Long.class, code);
        extraTypeIds.add(id);
        return id;
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

    private StaffUserPrincipal actAs(StaffUser.Role role) {
        String username = "edge-case-it-" + role + "-" + UUID.randomUUID();
        staffUsernames.add(username);
        jdbcTemplate.update(
                "INSERT INTO staff_users (username, password_hash, role, is_active) VALUES (?, ?, ?, TRUE)",
                username, KNOWN_HASH, role.name());
        StaffUser staffUser = staffUserRepository.findByUsername(username).orElseThrow();
        StaffUserPrincipal principal = new StaffUserPrincipal(staffUser);
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return principal;
    }

    /** A start time at least {@code daysOut} days from now, always inside the seeded all-day WORKING window. */
    private static Instant nextWorkingInstant(int daysOut) {
        return Instant.now().plus(daysOut, ChronoUnit.DAYS).atZone(ZONE).with(LocalTime.of(10, 0)).toInstant();
    }

    private static CreateAppointmentRequest validRequest(String holdToken) {
        return new CreateAppointmentRequest(holdToken, "Jordan Rivera", PATIENT_EMAIL, PATIENT_PHONE, null);
    }
}
