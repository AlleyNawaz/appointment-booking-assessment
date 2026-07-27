package com.clinic.booking.common.exception;

/**
 * Thrown when a requested date is earlier than today in the clinic's
 * configured timezone (PRD §8.4/§11.1). Mapped to
 * {@code 400 INVALID_APPOINTMENT_DATE} by {@link GlobalExceptionHandler}.
 */
public class InvalidAppointmentDateException extends RuntimeException {

    public InvalidAppointmentDateException() {
        super("The requested date has already passed.");
    }
}
