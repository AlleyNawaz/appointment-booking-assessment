package com.clinic.booking.common.exception;

/** §8.12: no {@code appointment_types} row for the given {@code id}. */
public class AppointmentTypeNotFoundException extends RuntimeException {

    public AppointmentTypeNotFoundException() {
        super("No appointment type was found for the given id.");
    }
}
