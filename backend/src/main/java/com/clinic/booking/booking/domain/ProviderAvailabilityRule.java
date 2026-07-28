package com.clinic.booking.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalTime;

/**
 * Maps to the {@code provider_availability_rules} table (PRD §7.4) — a
 * provider's recurring weekly working hours and breaks, keyed by
 * {@code day_of_week} (0=Sunday..6=Saturday).
 */
@Entity
@Table(name = "provider_availability_rules")
public class ProviderAvailabilityRule {

    public enum RuleType {
        WORKING, BREAK
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false)
    private RuleType ruleType;

    protected ProviderAvailabilityRule() {
        // required by JPA
    }

    public ProviderAvailabilityRule(Long providerId, Integer dayOfWeek, LocalTime startTime, LocalTime endTime,
            RuleType ruleType) {
        this.providerId = providerId;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.ruleType = ruleType;
    }

    /** §8.14 PUT — full replace of the mutable fields ({@code providerId} never changes on update). */
    public void update(Integer dayOfWeek, LocalTime startTime, LocalTime endTime, RuleType ruleType) {
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.ruleType = ruleType;
    }

    public Long getId() {
        return id;
    }

    public Long getProviderId() {
        return providerId;
    }

    public Integer getDayOfWeek() {
        return dayOfWeek;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public RuleType getRuleType() {
        return ruleType;
    }
}
