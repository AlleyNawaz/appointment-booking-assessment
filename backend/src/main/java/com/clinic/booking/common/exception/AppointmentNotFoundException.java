package com.clinic.booking.common.exception;

/**
 * §8.7/§15.3: an unknown or malformed {@code confirmationToken}. Deliberately
 * the same response for both cases — the lookup is a plain equality query,
 * so a malformed token simply never matches a row, with no separate
 * format-validation branch that could produce a distinguishable response.
 */
public class AppointmentNotFoundException extends RuntimeException {

    public AppointmentNotFoundException() {
        super("No appointment was found for the given confirmation token.");
    }
}
