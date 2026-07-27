package com.clinic.booking.audit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentAuditLogRepository extends JpaRepository<AppointmentAuditLog, Long> {
}
