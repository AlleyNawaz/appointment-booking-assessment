package com.clinic.booking.common.exception;

/**
 * Thrown when {@code providerId}/{@code appointmentTypeId} doesn't reference
 * an active row (PRD §11.9) — a soft-deleted or deactivated provider/type
 * returns this even if the ID once existed. Mapped to
 * {@code 409 PROVIDER_UNAVAILABLE} by {@link GlobalExceptionHandler}.
 */
public class ProviderUnavailableException extends RuntimeException {

    public ProviderUnavailableException() {
        super("The requested provider or appointment type is not currently available.");
    }
}
