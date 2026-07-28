package com.clinic.booking.common.exception;

/** §8.12: {@code appointment_types.code} unique-constraint violation on create/update. */
public class AppointmentTypeCodeExistsException extends RuntimeException {

    public AppointmentTypeCodeExistsException() {
        super("An appointment type with this code already exists.");
    }
}
