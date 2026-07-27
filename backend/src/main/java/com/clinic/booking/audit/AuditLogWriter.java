package com.clinic.booking.audit;

import com.clinic.booking.booking.domain.Appointment;
import org.springframework.stereotype.Service;

/** PRD §7.9/§12.7: writes exactly one {@code appointment_audit_log} row per status transition. */
@Service
public class AuditLogWriter {

    private final AppointmentAuditLogRepository repository;

    public AuditLogWriter(AppointmentAuditLogRepository repository) {
        this.repository = repository;
    }

    public void write(Long appointmentId, Appointment.Status previousStatus, Appointment.Status newStatus,
            String changedBy, String reason) {
        repository.save(new AppointmentAuditLog(
                appointmentId, previousStatus == null ? null : previousStatus.name(), newStatus.name(), changedBy,
                reason));
    }
}
