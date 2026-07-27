package com.clinic.booking.booking.dto;

import com.clinic.booking.booking.domain.Provider;

/**
 * Response shape for GET /booking/providers (PRD §8.3):
 * {@code { "id", "firstName", "lastName", "specialty" } }. {@code email},
 * {@code timezone}, and active/soft-delete state are entity-internal and
 * deliberately excluded from the patient-facing response.
 */
public record ProviderResponse(Long id, String firstName, String lastName, String specialty) {

    public static ProviderResponse from(Provider provider) {
        return new ProviderResponse(
                provider.getId(),
                provider.getFirstName(),
                provider.getLastName(),
                provider.getSpecialty());
    }
}
