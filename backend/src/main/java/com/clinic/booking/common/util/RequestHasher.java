package com.clinic.booking.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * PRD §8.6/§7.7: {@code request_body_hash = SHA256(holdToken + '|' + patientFullName + '|' +
 * lower(patientEmail) + '|' + patientPhone + '|' + (notes ?? ''))}, hex-encoded to 64 characters.
 * Computed over the canonicalized semantic fields (already-trimmed/validated values passed in by
 * the caller), not the raw request bytes, so retries with different whitespace/key order still
 * hash identically.
 */
public final class RequestHasher {

    private RequestHasher() {
    }

    public static String hash(String holdToken, String patientFullName, String patientEmail, String patientPhone,
            String notes) {
        String canonical = holdToken + '|' + patientFullName + '|' + patientEmail.toLowerCase() + '|' + patientPhone
                + '|' + (notes == null ? "" : notes);
        return digestHex(canonical);
    }

    /**
     * §12.13's idempotency paragraph: "the {@code Idempotency-Key} header on this endpoint
     * follows the identical replay contract as §8.6" — same hash-then-compare mechanism,
     * applied to the reschedule request's own fields (the original appointment's token, the
     * new hold token, and the optional reason) rather than a fresh booking's patient fields.
     */
    public static String hashReschedule(String confirmationToken, String holdToken, String reason) {
        String canonical = confirmationToken + '|' + holdToken + '|' + (reason == null ? "" : reason);
        return digestHex(canonical);
    }

    private static String digestHex(String canonical) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
