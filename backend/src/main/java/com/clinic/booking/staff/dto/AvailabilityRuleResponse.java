package com.clinic.booking.staff.dto;

import com.clinic.booking.booking.domain.ProviderAvailabilityRule;

import java.time.LocalTime;

/** Response shape for the availability-rule endpoints (PRD §8.14). */
public record AvailabilityRuleResponse(
        Long id, Long providerId, Integer dayOfWeek, LocalTime startTime, LocalTime endTime,
        ProviderAvailabilityRule.RuleType ruleType) {

    public static AvailabilityRuleResponse from(ProviderAvailabilityRule rule) {
        return new AvailabilityRuleResponse(
                rule.getId(), rule.getProviderId(), rule.getDayOfWeek(), rule.getStartTime(), rule.getEndTime(),
                rule.getRuleType());
    }
}
