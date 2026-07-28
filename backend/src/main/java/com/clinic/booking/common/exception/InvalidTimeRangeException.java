package com.clinic.booking.common.exception;

/** §8.14/§8.15: {@code startTime}/{@code startDatetime} is not strictly before the corresponding end value. */
public class InvalidTimeRangeException extends RuntimeException {

    public InvalidTimeRangeException() {
        super("The start of the range must be before the end.");
    }
}
