package com.clinic.booking.common.exception;

/**
 * Thrown when a required field is missing from a JSON request body — the
 * body-field counterpart to Spring's own {@code MissingServletRequestParameterException}
 * (used for query parameters, e.g. in {@code ProviderController}). Kept
 * distinct because that exception's name and semantics are specifically about
 * servlet request parameters, not body fields. Mapped to the same
 * {@code 400 VALIDATION_ERROR} by {@link GlobalExceptionHandler}.
 */
public class MissingRequiredFieldException extends RuntimeException {

    public MissingRequiredFieldException(String fieldName) {
        super("Required field '" + fieldName + "' is missing.");
    }
}
