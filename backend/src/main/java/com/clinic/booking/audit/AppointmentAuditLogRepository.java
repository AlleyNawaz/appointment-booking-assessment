package com.clinic.booking.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface AppointmentAuditLogRepository extends JpaRepository<AppointmentAuditLog, Long> {

    /** §8.18: every filter is optional (bound {@code null} skips that predicate). */
    @Query("SELECT a FROM AppointmentAuditLog a WHERE (:appointmentId IS NULL OR a.appointmentId = :appointmentId) "
            + "AND (:from IS NULL OR a.changedAt >= :from) "
            + "AND (:to IS NULL OR a.changedAt < :to)")
    Page<AppointmentAuditLog> search(
            @Param("appointmentId") Long appointmentId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);
}
