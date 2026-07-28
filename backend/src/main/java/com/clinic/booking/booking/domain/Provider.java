package com.clinic.booking.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Maps to the {@code providers} table (PRD §7.1). The {@code appointmentTypes}
 * association maps the {@code provider_appointment_types} join table (§7.3),
 * used by {@link com.clinic.booking.booking.repository.ProviderRepository} to
 * answer "which providers offer appointment type X" (§8.3).
 */
@Entity
@Table(name = "providers")
public class Provider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "specialty", nullable = false, length = 150)
    private String specialty;

    @Column(name = "email", nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    // Neither column is ever written by Hibernate — MySQL's DEFAULT CURRENT_TIMESTAMP(3)
    // (and, for updated_at, ON UPDATE CURRENT_TIMESTAMP(3)) assigns both entirely at the
    // DB layer; DEFAULT only applies when a column is omitted from the statement, not
    // when explicitly sent as NULL, so this table would otherwise reject any JPA insert.
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @ManyToMany
    @JoinTable(
            name = "provider_appointment_types",
            joinColumns = @JoinColumn(name = "provider_id"),
            inverseJoinColumns = @JoinColumn(name = "appointment_type_id"))
    private Set<AppointmentType> appointmentTypes = new LinkedHashSet<>();

    protected Provider() {
        // required by JPA
    }

    public Provider(String firstName, String lastName, String specialty, String email, String timezone,
            boolean active) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.specialty = specialty;
        this.email = email;
        this.timezone = timezone;
        this.active = active;
    }

    /** §8.13 PUT — full replace of the mutable fields. */
    public void update(String firstName, String lastName, String specialty, String email, String timezone,
            boolean active) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.specialty = specialty;
        this.email = email;
        this.timezone = timezone;
        this.active = active;
    }

    /** §8.13 DELETE — soft delete: {@code deleted_at = NOW()}, {@code is_active = FALSE}. */
    public void softDelete() {
        this.active = false;
        this.deletedAt = Instant.now();
    }

    /** §8.13 PUT .../appointment-types — replaces the full set. */
    public void replaceAppointmentTypes(Set<AppointmentType> types) {
        this.appointmentTypes.clear();
        this.appointmentTypes.addAll(types);
    }

    public Long getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getSpecialty() {
        return specialty;
    }

    public String getEmail() {
        return email;
    }

    public String getTimezone() {
        return timezone;
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

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Set<AppointmentType> getAppointmentTypes() {
        return appointmentTypes;
    }
}
