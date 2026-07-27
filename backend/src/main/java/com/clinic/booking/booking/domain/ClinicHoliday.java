package com.clinic.booking.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * Maps to the {@code clinic_holidays} table (PRD §7.6) — absolute for every
 * provider, no per-provider override (§12.4). {@code isRecurringAnnually} is
 * descriptive metadata only; the PRD defines no mechanism for deriving future
 * years' occurrences from it, so availability computation matches on the
 * exact {@code holiday_date} only (§11.5).
 */
@Entity
@Table(name = "clinic_holidays")
public class ClinicHoliday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "holiday_date", nullable = false, unique = true)
    private LocalDate holidayDate;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "is_recurring_annually", nullable = false)
    private boolean recurringAnnually;

    protected ClinicHoliday() {
        // required by JPA
    }

    public Long getId() {
        return id;
    }

    public LocalDate getHolidayDate() {
        return holidayDate;
    }

    public String getName() {
        return name;
    }

    public boolean isRecurringAnnually() {
        return recurringAnnually;
    }
}
