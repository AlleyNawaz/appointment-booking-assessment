package com.clinic.booking.common.exception;

/** §8.15: no {@code provider_unavailability} row for the given {@code id}. */
public class UnavailabilityNotFoundException extends RuntimeException {

    public UnavailabilityNotFoundException() {
        super("No unavailability record was found for the given id.");
    }
}
