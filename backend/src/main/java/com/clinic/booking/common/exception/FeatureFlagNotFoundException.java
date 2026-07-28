package com.clinic.booking.common.exception;

/** §8.17: {@code flagName} isn't a known row in {@code feature_flags}. */
public class FeatureFlagNotFoundException extends RuntimeException {

    public FeatureFlagNotFoundException() {
        super("No feature flag was found with the given name.");
    }
}
