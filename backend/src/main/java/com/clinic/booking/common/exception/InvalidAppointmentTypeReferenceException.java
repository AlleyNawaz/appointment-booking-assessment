package com.clinic.booking.common.exception;

/** §8.13: an id in {@code appointmentTypeIds} doesn't reference an existing {@code appointment_types} row. */
public class InvalidAppointmentTypeReferenceException extends RuntimeException {

    public InvalidAppointmentTypeReferenceException() {
        super("One or more appointment type ids do not exist.");
    }
}
