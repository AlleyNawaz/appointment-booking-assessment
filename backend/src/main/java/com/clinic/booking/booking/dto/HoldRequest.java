package com.clinic.booking.booking.dto;

import java.time.Instant;

/**
 * Request shape for POST /booking/holds (PRD §8.5):
 * {@code { "providerId": 5, "appointmentTypeId": 2, "startDateTime": "2026-08-15T13:00:00Z" } }.
 *
 * <p>Deliberately has no {@code @NotNull}/{@code @Valid} bean-validation
 * annotations — those would be enforced by Spring MVC during argument
 * resolution, which runs before {@code @FeatureGate}'s AOP advice fires,
 * letting a validation error leak ahead of the flag check (the same ordering
 * bug fixed for {@code ProviderController} in Milestone 2). Presence is
 * checked explicitly in {@code HoldController}'s method body instead.
 */
public record HoldRequest(Long providerId, Long appointmentTypeId, Instant startDateTime) {
}
