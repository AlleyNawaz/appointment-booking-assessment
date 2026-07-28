package com.clinic.booking.common.exception;

/**
 * §8.10/§10: a staff status-transition's {@code If-Match} version no longer
 * matches the row (a genuine concurrent write, §19 #15), or the appointment
 * isn't currently in the state the transition requires (an invalid
 * transition, e.g. {@code CANCELLED → COMPLETED}) — §19 #24 treats both as
 * the same {@code 409 STALE_VERSION} conflict rather than inventing a second
 * error code, since either way the client's view of the row is out of date.
 */
public class StaleVersionException extends RuntimeException {

    public StaleVersionException() {
        super("The appointment was modified since it was last read. Refresh and try again.");
    }
}
