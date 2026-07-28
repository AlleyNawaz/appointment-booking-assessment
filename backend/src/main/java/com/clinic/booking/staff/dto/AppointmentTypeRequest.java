package com.clinic.booking.staff.dto;

/** Request body for POST/PUT /staff/appointment-types (PRD §8.12). */
public record AppointmentTypeRequest(
        String code,
        String displayName,
        Integer durationMinutes,
        Integer bufferMinutes,
        Boolean requiresApproval,
        Boolean isActive) {
}
