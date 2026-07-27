package com.clinic.booking.booking.dto;

/** Optional request body for DELETE /booking/appointments/{confirmationToken} (PRD §8.8): {@code { "reason": "..." } }. */
public record CancelAppointmentRequest(String reason) {
}
