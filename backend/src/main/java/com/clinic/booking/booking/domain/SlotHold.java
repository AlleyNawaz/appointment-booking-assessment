package com.clinic.booking.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Maps to the {@code slot_holds} table (PRD §7.8) — the soft, UX-layer lock
 * (§12.10) that reserves a slot for {@code HOLD_DURATION_MINUTES} while a
 * patient fills out the contact form. {@code appointment_type_id} is
 * persisted here (not just accepted transiently in the request, §8.5) so it
 * survives to booking creation without the client resending it.
 */
@Entity
@Table(name = "slot_holds")
public class SlotHold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "appointment_type_id", nullable = false)
    private Long appointmentTypeId;

    @Column(name = "start_datetime", nullable = false)
    private Instant startDatetime;

    @Column(name = "end_datetime", nullable = false)
    private Instant endDatetime;

    @Column(name = "hold_token", nullable = false, unique = true)
    private String holdToken;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    // Not written by Hibernate at all — MySQL's DEFAULT CURRENT_TIMESTAMP(3) assigns it,
    // which only applies when the column is omitted from the INSERT statement entirely.
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected SlotHold() {
        // required by JPA
    }

    public SlotHold(Long providerId, Long appointmentTypeId, Instant startDatetime, Instant endDatetime,
            String holdToken, Instant expiresAt) {
        this.providerId = providerId;
        this.appointmentTypeId = appointmentTypeId;
        this.startDatetime = startDatetime;
        this.endDatetime = endDatetime;
        this.holdToken = holdToken;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public Long getProviderId() {
        return providerId;
    }

    public Long getAppointmentTypeId() {
        return appointmentTypeId;
    }

    public Instant getStartDatetime() {
        return startDatetime;
    }

    public Instant getEndDatetime() {
        return endDatetime;
    }

    public String getHoldToken() {
        return holdToken;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
