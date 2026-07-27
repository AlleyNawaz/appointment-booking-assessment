package com.clinic.booking.booking.dto;

import com.clinic.booking.booking.domain.Appointment;

import java.time.Instant;

/**
 * Response shape for POST /booking/appointments (PRD §8.6):
 * {@code { "confirmationToken", "status", "providerId", "startDateTime" } }.
 */
public record AppointmentResponse(String confirmationToken, Appointment.Status status, Long providerId,
        Instant startDateTime) {
}
