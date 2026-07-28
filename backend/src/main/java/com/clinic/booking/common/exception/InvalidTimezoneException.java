package com.clinic.booking.common.exception;

/** §8.13: {@code timezone} isn't a valid IANA zone identifier (validated against the JVM's {@code ZoneId} registry). */
public class InvalidTimezoneException extends RuntimeException {

    public InvalidTimezoneException() {
        super("The provided timezone is not a valid IANA timezone identifier.");
    }
}
