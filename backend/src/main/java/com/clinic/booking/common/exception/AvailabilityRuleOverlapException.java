package com.clinic.booking.common.exception;

/** §8.14/§19 #38: no two rules for the same provider + day of week may overlap, regardless of rule type. */
public class AvailabilityRuleOverlapException extends RuntimeException {

    public AvailabilityRuleOverlapException() {
        super("This rule overlaps an existing availability rule for the same provider and day.");
    }
}
