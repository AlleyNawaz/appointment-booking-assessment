package com.clinic.booking.common.exception;

/**
 * §12.13 step 6: a concurrent staff action (e.g. a cancellation from the
 * console) changed the original appointment's row between this reschedule
 * transaction's initial read and its optimistic-locked update — detected via
 * the same {@code version}/{@code @Version} mechanism as §12.12, surfaced
 * with its own error code rather than {@code STALE_VERSION} since no client
 * ever supplied a version here to become stale; this is a server-detected
 * concurrent change, not a client using an out-of-date value. Mapped to
 * {@code 409 APPOINTMENT_STATE_CHANGED} by {@link GlobalExceptionHandler}.
 */
public class AppointmentStateChangedException extends RuntimeException {

    public AppointmentStateChangedException() {
        super("This appointment was modified by clinic staff and can no longer be rescheduled. Refresh and try again.");
    }
}
