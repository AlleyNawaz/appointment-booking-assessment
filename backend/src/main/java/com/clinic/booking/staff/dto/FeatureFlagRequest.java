package com.clinic.booking.staff.dto;

/** Request body for PUT /staff/feature-flags/{flagName} (PRD §8.17). */
public record FeatureFlagRequest(Boolean isEnabled) {
}
