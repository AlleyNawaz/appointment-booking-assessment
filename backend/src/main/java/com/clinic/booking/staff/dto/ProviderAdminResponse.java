package com.clinic.booking.staff.dto;

import com.clinic.booking.booking.domain.Provider;

import java.util.List;

/** Staff-facing provider shape (PRD §8.13) — includes {@code id} and {@code appointmentTypeIds}. */
public record ProviderAdminResponse(
        Long id,
        String firstName,
        String lastName,
        String specialty,
        String email,
        String timezone,
        boolean isActive,
        List<Long> appointmentTypeIds) {

    public static ProviderAdminResponse from(Provider provider) {
        return new ProviderAdminResponse(
                provider.getId(),
                provider.getFirstName(),
                provider.getLastName(),
                provider.getSpecialty(),
                provider.getEmail(),
                provider.getTimezone(),
                provider.isActive(),
                provider.getAppointmentTypes().stream().map(t -> t.getId()).toList());
    }
}
