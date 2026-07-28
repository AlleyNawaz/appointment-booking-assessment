package com.clinic.booking.common.exception;

/** §8.16: no {@code clinic_holidays} row for the given {@code id}. */
public class HolidayNotFoundException extends RuntimeException {

    public HolidayNotFoundException() {
        super("No holiday was found for the given id.");
    }
}
