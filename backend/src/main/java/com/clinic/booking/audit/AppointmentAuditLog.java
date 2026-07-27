package com.clinic.booking.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Maps to the {@code appointment_audit_log} table (PRD §7.9) — one row per
 * appointment status transition. {@code changed_by} is exactly one of
 * {@code 'SYSTEM'}, {@code 'PATIENT_SELF_SERVICE'}, or a
 * {@code staff_users.username} value.
 */
@Entity
@Table(name = "appointment_audit_log")
public class AppointmentAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "appointment_id", nullable = false)
    private Long appointmentId;

    @Column(name = "previous_status")
    private String previousStatus;

    @Column(name = "new_status", nullable = false)
    private String newStatus;

    @Column(name = "changed_by", nullable = false)
    private String changedBy;

    @Column(name = "reason")
    private String reason;

    // Never written by Hibernate — MySQL's DEFAULT CURRENT_TIMESTAMP(3) assigns it;
    // see Provider.createdAt (Milestone 1/4) for why insertable must be false.
    @Column(name = "changed_at", nullable = false, insertable = false, updatable = false)
    private Instant changedAt;

    protected AppointmentAuditLog() {
        // required by JPA
    }

    public AppointmentAuditLog(Long appointmentId, String previousStatus, String newStatus, String changedBy,
            String reason) {
        this.appointmentId = appointmentId;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.changedBy = changedBy;
        this.reason = reason;
    }

    public Long getId() {
        return id;
    }

    public Long getAppointmentId() {
        return appointmentId;
    }

    public String getPreviousStatus() {
        return previousStatus;
    }

    public String getNewStatus() {
        return newStatus;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public String getReason() {
        return reason;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
