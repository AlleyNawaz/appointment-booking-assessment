package com.clinic.booking.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Maps to the {@code feature_flags} table (PRD §7.9). Read-only in this
 * milestone — the toggle endpoint (§8.17) that writes to this table is a later
 * milestone, so no setters exist yet.
 */
@Entity
@Table(name = "feature_flags")
public class FeatureFlag {

    @Id
    @Column(name = "flag_name")
    private String flagName;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FeatureFlag() {
        // required by JPA
    }

    /** Package-private: only used to construct fixtures in tests within this package. */
    FeatureFlag(String flagName, boolean enabled, String updatedBy, Instant updatedAt) {
        this.flagName = flagName;
        this.enabled = enabled;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    public String getFlagName() {
        return flagName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
