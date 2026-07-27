package com.clinic.booking.common.exception;

/** §11.4/§11.5: the requested date has no provider WORKING rule, or is a clinic holiday. */
public class ClinicClosedDayException extends RuntimeException {

    public ClinicClosedDayException() {
        super("The clinic or provider is not open on the requested date.");
    }
}
