package com.clinic.booking.staff.dto;

import com.clinic.booking.booking.domain.AppointmentType;

/** Staff-facing appointment-type shape (PRD §8.12) — includes every field, unlike the patient-facing response. */
public record AppointmentTypeAdminResponse(
        Long id,
        String code,
        String displayName,
        Integer durationMinutes,
        Integer bufferMinutes,
        boolean requiresApproval,
        boolean isActive) {

    public static AppointmentTypeAdminResponse from(AppointmentType type) {
        return new AppointmentTypeAdminResponse(
                type.getId(),
                type.getCode(),
                type.getDisplayName(),
                type.getDurationMinutes(),
                type.getBufferMinutes(),
                type.isRequiresApproval(),
                type.isActive());
    }
}
