package com.clinic.booking.common.exception;

/**
 * Thrown by {@link com.clinic.booking.config.FeatureGateAspect} when a gated
 * endpoint is called while {@code enable_online_booking} is off. Mapped to
 * {@code 403 FEATURE_DISABLED} by {@link GlobalExceptionHandler} (PRD §6/§13).
 */
public class FeatureDisabledException extends RuntimeException {

    public FeatureDisabledException() {
        super("Online booking is currently unavailable.");
    }
}
