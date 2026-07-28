package com.clinic.booking.common.exception;

/** §8.13: {@code providers.email} unique-constraint violation on create/update. */
public class ProviderEmailExistsException extends RuntimeException {

    public ProviderEmailExistsException() {
        super("A provider with this email already exists.");
    }
}
