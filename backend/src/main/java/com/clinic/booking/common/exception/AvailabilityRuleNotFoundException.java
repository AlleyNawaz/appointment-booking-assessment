package com.clinic.booking.common.exception;

/** §8.14: no {@code provider_availability_rules} row for the given {@code id}. */
public class AvailabilityRuleNotFoundException extends RuntimeException {

    public AvailabilityRuleNotFoundException() {
        super("No availability rule was found for the given id.");
    }
}
