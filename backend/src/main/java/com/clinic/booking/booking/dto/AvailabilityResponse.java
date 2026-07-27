package com.clinic.booking.booking.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Response shape for GET /booking/availability (PRD §8.4):
 * {@code { "date": "2026-08-15", "slots": ["2026-08-15T13:00:00Z", ...] } }.
 */
public record AvailabilityResponse(LocalDate date, List<Instant> slots) {
}
