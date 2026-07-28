package com.clinic.booking.booking.dto;

import com.clinic.booking.booking.domain.Appointment;

import java.time.Instant;

/**
 * Response shape for POST /booking/appointments/{confirmationToken}/reschedule
 * (PRD §8.19): {@code { "confirmationToken", "status", "providerId", "startDateTime",
 * "previousConfirmationToken" } }.
 */
public record RescheduleResponse(
        String confirmationToken,
        Appointment.Status status,
        Long providerId,
        Instant startDateTime,
        String previousConfirmationToken) {
}
