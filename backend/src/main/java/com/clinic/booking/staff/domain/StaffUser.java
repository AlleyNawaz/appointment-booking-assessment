package com.clinic.booking.staff.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Maps to the {@code staff_users} table (PRD §7.12). {@code chk_provider_role_pairing}
 * is a hard DB invariant (not re-validated here) — a {@code ROLE_PROVIDER} row can
 * never exist without a {@code provider_id}.
 */
@Entity
@Table(name = "staff_users")
public class StaffUser {

    public enum Role {
        ROLE_STAFF, ROLE_PROVIDER, ROLE_ADMIN, ROLE_SYSADMIN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @Column(name = "provider_id")
    private Long providerId;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    // Never written by Hibernate — MySQL's DEFAULT CURRENT_TIMESTAMP(3) assigns both;
    // see Provider.createdAt (Milestone 1/4) for why insertable/updatable must be false.
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected StaffUser() {
        // required by JPA
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public Long getProviderId() {
        return providerId;
    }

    public boolean isActive() {
        return active;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** §15.9: a wrong attempt increments the counter and, on hitting the threshold, locks the account. */
    public void recordFailedLogin(int maxFailedAttempts, int lockoutMinutes) {
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= maxFailedAttempts) {
            this.lockedUntil = Instant.now().plusSeconds(lockoutMinutes * 60L);
        }
    }

    /** §15.9: only a successful login resets the failure counter — lockout never self-resets it. */
    public void recordSuccessfulLogin() {
        this.failedLoginAttempts = 0;
        this.lastLoginAt = Instant.now();
    }

    /** §15.9: {@code locked_until} is a timestamp, not a boolean — the lock self-expires at read time. */
    public boolean isCurrentlyLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }
}
