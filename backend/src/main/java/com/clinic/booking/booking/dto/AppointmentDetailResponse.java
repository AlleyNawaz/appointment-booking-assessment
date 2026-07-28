package com.clinic.booking.booking.dto;

import com.clinic.booking.booking.domain.Appointment;

import java.time.Instant;

/**
 * Response shape for GET/DELETE /booking/appointments/{confirmationToken}
 * (PRD §8.7/§8.8): "full appointment detail (provider name, type, time,
 * status, cancellation eligibility)". {@code providerId}/{@code appointmentTypeId}
 * are additive fields (not named individually by §8.7's prose contract) needed
 * so the Angular reschedule action (§8.19/§12.13) can query availability/holds
 * for the same provider and type without a second lookup round-trip.
 */
public record AppointmentDetailResponse(
        String confirmationToken,
        String providerName,
        String appointmentTypeName,
        Instant startDateTime,
        Appointment.Status status,
        boolean cancellationEligible,
        Long providerId,
        Long appointmentTypeId) {
}
