package com.clinic.booking.common.exception;

/**
 * §12.13 step 1: no state-diagram edge exists for rescheduling anything but a
 * {@code CONFIRMED} appointment (§12.7) — attempting to reschedule a
 * {@code PENDING}/{@code CANCELLED}/{@code COMPLETED}/{@code REJECTED}/
 * {@code EXPIRED}/{@code MISSED} appointment fails immediately with this.
 * Mapped to {@code 409 APPOINTMENT_NOT_RESCHEDULABLE} by {@link GlobalExceptionHandler}.
 */
public class AppointmentNotReschedulableException extends RuntimeException {

    public AppointmentNotReschedulableException() {
        super("This appointment cannot be rescheduled in its current status.");
    }
}
