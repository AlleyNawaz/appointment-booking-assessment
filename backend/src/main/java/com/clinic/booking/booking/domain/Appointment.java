package com.clinic.booking.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * Maps to the {@code appointments} table (PRD §7.7) — the core booking
 * record. {@code activeSlotKey} is a DB-generated stored column
 * ({@code insertable/updatable = false}); {@code version} backs the
 * optimistic-locking mechanism used by later staff-status-transition
 * milestones (§12.12).
 */
@Entity
@Table(name = "appointments")
public class Appointment {

    public enum Status {
        PENDING, CONFIRMED, CANCELLED, COMPLETED, REJECTED, EXPIRED, MISSED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "confirmation_token", nullable = false, unique = true)
    private String confirmationToken;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "appointment_type_id", nullable = false)
    private Long appointmentTypeId;

    @Column(name = "patient_full_name", nullable = false)
    private String patientFullName;

    @Column(name = "patient_email", nullable = false)
    private String patientEmail;

    @Column(name = "patient_phone", nullable = false)
    private String patientPhone;

    @Column(name = "notes")
    private String notes;

    @Column(name = "start_datetime", nullable = false)
    private Instant startDatetime;

    @Column(name = "end_datetime", nullable = false)
    private Instant endDatetime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "request_body_hash", nullable = false)
    private String requestBodyHash;

    @Column(name = "active_slot_key", insertable = false, updatable = false)
    private String activeSlotKey;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    // Never written by Hibernate — MySQL's DEFAULT CURRENT_TIMESTAMP(3) assigns both;
    // see Provider.createdAt (Milestone 1/4) for why insertable/updatable must be false.
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Appointment() {
        // required by JPA
    }

    public Appointment(String confirmationToken, Long providerId, Long appointmentTypeId, String patientFullName,
            String patientEmail, String patientPhone, String notes, Instant startDatetime, Instant endDatetime,
            Status status, String idempotencyKey, String requestBodyHash) {
        this.confirmationToken = confirmationToken;
        this.providerId = providerId;
        this.appointmentTypeId = appointmentTypeId;
        this.patientFullName = patientFullName;
        this.patientEmail = patientEmail;
        this.patientPhone = patientPhone;
        this.notes = notes;
        this.startDatetime = startDatetime;
        this.endDatetime = endDatetime;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.requestBodyHash = requestBodyHash;
    }

    public Long getId() {
        return id;
    }

    public String getConfirmationToken() {
        return confirmationToken;
    }

    public Long getProviderId() {
        return providerId;
    }

    public Long getAppointmentTypeId() {
        return appointmentTypeId;
    }

    public String getPatientFullName() {
        return patientFullName;
    }

    public String getPatientEmail() {
        return patientEmail;
    }

    public String getPatientPhone() {
        return patientPhone;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getStartDatetime() {
        return startDatetime;
    }

    public Instant getEndDatetime() {
        return endDatetime;
    }

    public Status getStatus() {
        return status;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestBodyHash() {
        return requestBodyHash;
    }

    public String getActiveSlotKey() {
        return activeSlotKey;
    }

    public int getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
