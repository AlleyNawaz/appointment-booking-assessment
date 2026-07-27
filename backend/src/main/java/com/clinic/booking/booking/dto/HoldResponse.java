package com.clinic.booking.booking.dto;

import java.time.Instant;

/**
 * Response shape for POST /booking/holds (PRD §8.5):
 * {@code { "holdToken": "b3f1...", "expiresAt": "2026-08-15T13:05:00Z" } }.
 */
public record HoldResponse(String holdToken, Instant expiresAt) {
}
