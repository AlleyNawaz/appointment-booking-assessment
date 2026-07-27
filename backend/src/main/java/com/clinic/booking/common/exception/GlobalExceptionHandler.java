package com.clinic.booking.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String errorCode, String message,
            HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(), status.value(), errorCode, message, request.getRequestURI(), List.of());
        return ResponseEntity.status(status).body(body);
    }
}
