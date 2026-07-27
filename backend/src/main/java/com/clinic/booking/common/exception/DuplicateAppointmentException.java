package com.clinic.booking.common.exception;

/** §11.7: same patient identity already holds an active, overlapping appointment with this provider. */
public class DuplicateAppointmentException extends RuntimeException {

    public DuplicateAppointmentException() {
        super("You already have an active appointment with this provider at this time.");
    }
}
