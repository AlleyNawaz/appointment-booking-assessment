package com.clinic.booking.staff.dto;

import java.time.Instant;

/** Request body for POST /staff/providers/{providerId}/unavailability (PRD §8.15). */
public record UnavailabilityRequest(Instant startDatetime, Instant endDatetime, String reason) {
}
