package com.clinic.booking.staff.dto;

import com.clinic.booking.booking.domain.ProviderAvailabilityRule;

import java.time.LocalTime;

/** Request body for POST/PUT availability-rule endpoints (PRD §8.14). */
public record AvailabilityRuleRequest(
        Integer dayOfWeek, LocalTime startTime, LocalTime endTime, ProviderAvailabilityRule.RuleType ruleType) {
}
