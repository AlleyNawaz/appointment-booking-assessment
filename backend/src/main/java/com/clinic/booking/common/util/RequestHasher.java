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
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
