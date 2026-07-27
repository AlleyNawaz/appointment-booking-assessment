package com.clinic.booking.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method as gated by a feature flag (PRD §6/§10). Resolved
 * by {@link FeatureGateAspect}, which checks the flag before the method body
 * runs — this is what makes "check the flag first" structural rather than an
 * inline {@code if} a controller author could forget (§10).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface FeatureGate {

    /** The {@code feature_flags.flag_name} to check, e.g. {@link FeatureFlagService#ENABLE_ONLINE_BOOKING}. */
    String value();
}
