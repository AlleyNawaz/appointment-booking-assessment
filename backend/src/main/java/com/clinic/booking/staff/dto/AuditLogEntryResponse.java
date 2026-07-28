package com.clinic.booking.staff.dto;

import com.clinic.booking.audit.AppointmentAuditLog;

import java.time.Instant;

/** One row of GET /staff/audit-log (PRD §8.18). */
public record AuditLogEntryResponse(
        Long appointmentId, String previousStatus, String newStatus, String changedBy, String reason,
        Instant changedAt) {

    public static AuditLogEntryResponse from(AppointmentAuditLog log) {
        return new AuditLogEntryResponse(
                log.getAppointmentId(), log.getPreviousStatus(), log.getNewStatus(), log.getChangedBy(),
                log.getReason(), log.getChangedAt());
    }
}
