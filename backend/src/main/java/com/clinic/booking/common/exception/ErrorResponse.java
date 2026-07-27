package com.clinic.booking.common.exception;

import java.time.Instant;
import java.util.List;

/**
 * The error envelope every error response uses (PRD §8):
 * {@code { "timestamp", "status", "errorCode", "message", "path", "fieldErrors" } }.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String errorCode,
        String message,
        String path,
        List<Object> fieldErrors) {
}
