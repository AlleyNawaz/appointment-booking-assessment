package com.clinic.booking.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Maps to the {@code appointment_types} table (PRD §7.2).
 */
@Entity
@Table(name = "appointment_types")
public class AppointmentType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(name = "buffer_minutes", nullable = false)
    private Integer bufferMinutes;

    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    // Neither column is ever written by Hibernate — see Provider's identical fields for why.
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected AppointmentType() {
        // required by JPA
    }

    public AppointmentType(String code, String displayName, Integer durationMinutes, Integer bufferMinutes,
            boolean requiresApproval, boolean active) {
        this.code = code;
        this.displayName = displayName;
        this.durationMinutes = durationMinutes;
        this.bufferMinutes = bufferMinutes;
        this.requiresApproval = requiresApproval;
        this.active = active;
    }

    /** §8.12 PUT — full replace of the mutable fields. */
    public void update(String code, String displayName, Integer durationMinutes, Integer bufferMinutes,
            boolean requiresApproval, boolean active) {
        this.code = code;
        this.displayName = displayName;
        this.durationMinutes = durationMinutes;
        this.bufferMinutes = bufferMinutes;
        this.requiresApproval = requiresApproval;
        this.active = active;
    }

    /** §8.12 DELETE — deactivates only; this table has no {@code deleted_at} (§7.2). */
    public void deactivate() {
        this.active = false;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public Integer getBufferMinutes() {
        return bufferMinutes;
    }

    public boolean isRequiresApproval() {
        return requiresApproval;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
