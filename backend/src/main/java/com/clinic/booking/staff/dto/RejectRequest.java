package com.clinic.booking.staff.dto;

/** Request body for POST /staff/appointments/{id}/reject (PRD §8.10) — {@code reason} is required. */
public record RejectRequest(String reason) {
}
