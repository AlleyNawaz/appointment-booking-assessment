package com.clinic.booking.common.exception;

/**
 * §8.13/§8.14/§8.15: no {@code providers} row for the given {@code id} — distinct from
 * {@link ProviderUnavailableException}, which is a booking-time referential-active check,
 * not an admin-input existence check (§8.13).
 */
public class ProviderNotFoundException extends RuntimeException {

    public ProviderNotFoundException() {
        super("No provider was found for the given id.");
    }
}
