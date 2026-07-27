package com.clinic.booking.staff.dto;

import java.time.Instant;

/**
 * Response shape for POST /staff/auth/login and GET /staff/auth/session (PRD §8.20):
 * {@code { "username", "role", "providerId", "sessionExpiresAt" } }.
 */
public record StaffSessionResponse(String username, String role, Long providerId, Instant sessionExpiresAt) {
}
