package com.clinic.booking.common.exception;

/** §8.16: {@code clinic_holidays.holiday_date} unique-constraint violation on create/update. */
public class HolidayDateExistsException extends RuntimeException {

    public HolidayDateExistsException() {
        super("A holiday for this date already exists.");
    }
}
