package com.clinic.booking.booking.validation;

import com.clinic.booking.booking.domain.ProviderAvailabilityRule;
import com.clinic.booking.booking.repository.ClinicHolidayRepository;
import com.clinic.booking.booking.repository.ProviderAvailabilityRuleRepository;
import com.clinic.booking.common.exception.ClinicClosedDayException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * §11.4 (weekend blocked unless the provider has an explicit WORKING rule for
 * that day of week) and §11.5 (clinic holiday blocks every provider, no
 * override) — both map to {@code CLINIC_CLOSED_DAY}.
 */
@Component
public class ClinicClosedDayValidator {

    private final ProviderAvailabilityRuleRepository availabilityRuleRepository;
    private final ClinicHolidayRepository clinicHolidayRepository;

    public ClinicClosedDayValidator(
            ProviderAvailabilityRuleRepository availabilityRuleRepository,
            ClinicHolidayRepository clinicHolidayRepository) {
        this.availabilityRuleRepository = availabilityRuleRepository;
        this.clinicHolidayRepository = clinicHolidayRepository;
    }

    public void validate(Long providerId, Instant startDateTime, String providerTimezone) {
        ZoneId zone = ZoneId.of(providerTimezone);
        LocalDate date = ZonedDateTime.ofInstant(startDateTime, zone).toLocalDate();

        if (clinicHolidayRepository.existsByHolidayDate(date)) {
            throw new ClinicClosedDayException();
        }

        int dayOfWeek = date.getDayOfWeek().getValue() % 7; // Mon=1..Sun=7 -> Sun=0..Sat=6, per §7.4
        List<ProviderAvailabilityRule> rules =
                availabilityRuleRepository.findByProviderIdAndDayOfWeek(providerId, dayOfWeek);
        boolean hasWorkingRule = rules.stream()
                .anyMatch(rule -> rule.getRuleType() == ProviderAvailabilityRule.RuleType.WORKING);
        if (!hasWorkingRule) {
            throw new ClinicClosedDayException();
        }
    }
}
