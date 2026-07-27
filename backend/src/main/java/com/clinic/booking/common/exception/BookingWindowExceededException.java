package com.clinic.booking.common.exception;

/**
 * Thrown when a requested date is more than {@code MAX_BOOKING_WINDOW_DAYS}
 * (90) days out (PRD §8.4/§11.3). Mapped to
 * {@code 400 BOOKING_WINDOW_EXCEEDED} by {@link GlobalExceptionHandler}.
 */
public class BookingWindowExceededException extends RuntimeException {

    public BookingWindowExceededException() {
        super("The requested date is too far in the future.");
    }
}
