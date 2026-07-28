package com.clinic.booking.staff.dto;

import com.clinic.booking.booking.domain.Appointment;
import com.clinic.booking.booking.domain.ProviderUnavailability;

import java.time.Instant;
import java.util.List;

/** Response shape for the unavailability endpoints (PRD §8.15). */
public record UnavailabilityResponse(
        Long id,
        Long providerId,
        Instant startDatetime,
        Instant endDatetime,
        String reason,
        String createdBy,
        List<AffectedAppointment> affectedAppointments) {

    /** §7.5's {@code needs_attention} surface — every PENDING/CONFIRMED appointment overlapping the new range. */
    public record AffectedAppointment(String confirmationToken, Instant startDatetime, Appointment.Status status) {

        public static AffectedAppointment from(Appointment appointment) {
            return new AffectedAppointment(
                    appointment.getConfirmationToken(), appointment.getStartDatetime(), appointment.getStatus());
        }
    }

    public static UnavailabilityResponse from(ProviderUnavailability unavailability, List<Appointment> affected) {
        return new UnavailabilityResponse(
                unavailability.getId(),
                unavailability.getProviderId(),
                unavailability.getStartDatetime(),
                unavailability.getEndDatetime(),
                unavailability.getReason(),
                unavailability.getCreatedBy(),
                affected.stream().map(AffectedAppointment::from).toList());
    }

    public static UnavailabilityResponse from(ProviderUnavailability unavailability) {
        return from(unavailability, List.of());
    }
}
