package com.clinic.booking.common.exception;

/**
 * Thrown when a present request-body field fails a §11 format/length rule
 * (e.g. {@code patientEmail} not RFC 5322-shaped, {@code patientPhone} not
 * E.164). Distinct from {@link MissingRequiredFieldException}, which is for
 * absent fields, not malformed ones — both map to the same
 * {@code 400 VALIDATION_ERROR} envelope.
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String fieldName, String reason) {
        super("Field '" + fieldName + "' is invalid: " + reason);
    }
}
