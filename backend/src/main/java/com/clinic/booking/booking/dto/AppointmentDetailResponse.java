package com.clinic.booking.booking.dto;

import com.clinic.booking.booking.domain.Appointment;

import java.time.Instant;

/**
 * Response shape for GET/DELETE /booking/appointments/{confirmationToken}
 * (PRD §8.7/§8.8): "full appointment detail (provider name, type, time,
 * status, cancellation eligibility)".
 */
public record AppointmentDetailResponse(
        String confirmationToken,
        String providerName,
        String appointmentTypeName,
        Instant startDateTime,
        Appointment.Status status,
        boolean cancellationEligible) {
}
