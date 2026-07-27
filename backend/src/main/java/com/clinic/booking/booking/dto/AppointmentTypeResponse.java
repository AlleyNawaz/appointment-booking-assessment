package com.clinic.booking.booking.dto;

import com.clinic.booking.booking.domain.AppointmentType;

/**
 * Response shape for GET /booking/appointment-types (PRD §8.2):
 * {@code { "id", "code", "displayName", "durationMinutes", "requiresApproval" } }.
 * {@code bufferMinutes} and {@code isActive} are entity-internal (buffer is an
 * availability-computation detail, §7.2; active types are the only ones ever
 * queried, so the flag itself is never patient-facing) and are deliberately
 * excluded, per §10's rule that entities are never serialized directly.
 */
public record AppointmentTypeResponse(
        Long id,
        String code,
        String displayName,
        Integer durationMinutes,
        boolean requiresApproval) {

    public static AppointmentTypeResponse from(AppointmentType type) {
        return new AppointmentTypeResponse(
                type.getId(),
                type.getCode(),
                type.getDisplayName(),
                type.getDurationMinutes(),
                type.isRequiresApproval());
    }
}
