package com.clinic.booking.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

/**
 * Maps every domain exception to the error envelope (PRD §8/§10). Each
 * business-rule violation gets its own typed exception rather than a generic
 * one, so this advice maps deterministically to the correct {@code errorCode}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(FeatureDisabledException.class)
    public ResponseEntity<ErrorResponse> handleFeatureDisabled(FeatureDisabledException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "FEATURE_DISABLED", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidAppointmentDateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidAppointmentDate(
            InvalidAppointmentDateException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_APPOINTMENT_DATE", ex.getMessage(), request);
    }

    @ExceptionHandler(BookingWindowExceededException.class)
    public ResponseEntity<ErrorResponse> handleBookingWindowExceeded(
            BookingWindowExceededException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "BOOKING_WINDOW_EXCEEDED", ex.getMessage(), request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Required parameter '" + ex.getParameterName() + "' is missing.", request);
    }

    @ExceptionHandler(MissingRequiredFieldException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequiredField(
            MissingRequiredFieldException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage(), request);
    }

    /**
     * A syntactically malformed JSON body fails during Spring MVC argument
     * resolution — e.g. an unparsable date on {@code HoldRequest.startDateTime}
     * — before any controller method (and any {@code @FeatureGate} advice on
     * it) ever runs, so this can't be deferred like a missing/null field can.
     * Mapped here instead of left to Spring Boot's default error page so the
     * response still matches the PRD §8 error envelope.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedRequestBody(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Malformed request body.", request);
    }

    @ExceptionHandler(SlotAlreadyBookedException.class)
    public ResponseEntity<ErrorResponse> handleSlotAlreadyBooked(
            SlotAlreadyBookedException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "SLOT_ALREADY_BOOKED", ex.getMessage(), request);
    }

    @ExceptionHandler(ProviderUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleProviderUnavailable(
            ProviderUnavailableException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "PROVIDER_UNAVAILABLE", ex.getMessage(), request);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage(), request);
    }

    @ExceptionHandler(LeadTimeViolationException.class)
    public ResponseEntity<ErrorResponse> handleLeadTimeViolation(
            LeadTimeViolationException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "LEAD_TIME_VIOLATION", ex.getMessage(), request);
    }

    @ExceptionHandler(ClinicClosedDayException.class)
    public ResponseEntity<ErrorResponse> handleClinicClosedDay(
            ClinicClosedDayException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "CLINIC_CLOSED_DAY", ex.getMessage(), request);
    }

    @ExceptionHandler(SlotHoldExpiredException.class)
    public ResponseEntity<ErrorResponse> handleSlotHoldExpired(
            SlotHoldExpiredException ex, HttpServletRequest request) {
        return build(HttpStatus.GONE, "SLOT_HOLD_EXPIRED", ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateAppointmentException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateAppointment(
            DuplicateAppointmentException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "DUPLICATE_APPOINTMENT", ex.getMessage(), request);
    }

    @ExceptionHandler(PatientDailyLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handlePatientDailyLimitExceeded(
            PatientDailyLimitExceededException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "PATIENT_DAILY_LIMIT_EXCEEDED", ex.getMessage(), request);
    }

    @ExceptionHandler(IdempotencyKeyReusedMismatchException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyKeyReusedMismatch(
            IdempotencyKeyReusedMismatchException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED_MISMATCH", ex.getMessage(), request);
    }

    @ExceptionHandler(AppointmentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAppointmentNotFound(
            AppointmentNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(CancellationWindowExpiredException.class)
    public ResponseEntity<ErrorResponse> handleCancellationWindowExpired(
            CancellationWindowExpiredException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "CANCELLATION_WINDOW_EXPIRED", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", ex.getMessage(), request);
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ErrorResponse> handleAccountLocked(
            AccountLockedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "ACCOUNT_LOCKED", ex.getMessage(), request);
    }

    @ExceptionHandler(StaleVersionException.class)
    public ResponseEntity<ErrorResponse> handleStaleVersion(StaleVersionException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "STALE_VERSION", ex.getMessage(), request);
    }

    @ExceptionHandler(AppointmentTypeCodeExistsException.class)
    public ResponseEntity<ErrorResponse> handleAppointmentTypeCodeExists(
            AppointmentTypeCodeExistsException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "APPOINTMENT_TYPE_CODE_EXISTS", ex.getMessage(), request);
    }

    @ExceptionHandler(AppointmentTypeNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAppointmentTypeNotFound(
            AppointmentTypeNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "APPOINTMENT_TYPE_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(ProviderEmailExistsException.class)
    public ResponseEntity<ErrorResponse> handleProviderEmailExists(
            ProviderEmailExistsException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "PROVIDER_EMAIL_EXISTS", ex.getMessage(), request);
    }

    @ExceptionHandler(ProviderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleProviderNotFound(
            ProviderNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "PROVIDER_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidTimezoneException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTimezone(
            InvalidTimezoneException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_TIMEZONE", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidAppointmentTypeReferenceException.class)
    public ResponseEntity<ErrorResponse> handleInvalidAppointmentTypeReference(
            InvalidAppointmentTypeReferenceException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_APPOINTMENT_TYPE_REFERENCE", ex.getMessage(), request);
    }

    @ExceptionHandler(AvailabilityRuleOverlapException.class)
    public ResponseEntity<ErrorResponse> handleAvailabilityRuleOverlap(
            AvailabilityRuleOverlapException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "AVAILABILITY_RULE_OVERLAP", ex.getMessage(), request);
    }

    @ExceptionHandler(AvailabilityRuleNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAvailabilityRuleNotFound(
            AvailabilityRuleNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "AVAILABILITY_RULE_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidTimeRangeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTimeRange(
            InvalidTimeRangeException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_TIME_RANGE", ex.getMessage(), request);
    }

    @ExceptionHandler(UnavailabilityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUnavailabilityNotFound(
            UnavailabilityNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "UNAVAILABILITY_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(HolidayDateExistsException.class)
    public ResponseEntity<ErrorResponse> handleHolidayDateExists(
            HolidayDateExistsException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "HOLIDAY_DATE_EXISTS", ex.getMessage(), request);
    }

    @ExceptionHandler(HolidayNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleHolidayNotFound(
            HolidayNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "HOLIDAY_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(FeatureFlagNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleFeatureFlagNotFound(
            FeatureFlagNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "FEATURE_FLAG_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(AppointmentNotReschedulableException.class)
    public ResponseEntity<ErrorResponse> handleAppointmentNotReschedulable(
            AppointmentNotReschedulableException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "APPOINTMENT_NOT_RESCHEDULABLE", ex.getMessage(), request);
    }

    @ExceptionHandler(AppointmentStateChangedException.class)
    public ResponseEntity<ErrorResponse> handleAppointmentStateChanged(
            AppointmentStateChangedException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "APPOINTMENT_STATE_CHANGED", ex.getMessage(), request);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String errorCode, String message,
            HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(), status.value(), errorCode, message, request.getRequestURI(), List.of());
        return ResponseEntity.status(status).body(body);
    }
}
