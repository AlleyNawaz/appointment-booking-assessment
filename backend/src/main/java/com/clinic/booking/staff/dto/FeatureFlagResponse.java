package com.clinic.booking.staff.dto;

import com.clinic.booking.config.FeatureFlag;

import java.time.Instant;

/** Response shape for the feature-flag endpoints (PRD §8.17). */
public record FeatureFlagResponse(String flagName, boolean isEnabled, String updatedBy, Instant updatedAt) {

    public static FeatureFlagResponse from(FeatureFlag flag) {
        return new FeatureFlagResponse(flag.getFlagName(), flag.isEnabled(), flag.getUpdatedBy(), flag.getUpdatedAt());
    }
}
