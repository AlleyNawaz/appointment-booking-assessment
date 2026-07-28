package com.clinic.booking.booking.repository;

import com.clinic.booking.booking.domain.ProviderAvailabilityRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProviderAvailabilityRuleRepository extends JpaRepository<ProviderAvailabilityRule, Long> {

    /**
     * All WORKING/BREAK rules for a provider on a given day of week (0=Sunday..6=Saturday),
     * the raw input to §8.4's "WORKING minus BREAK" availability computation.
     */
    List<ProviderAvailabilityRule> findByProviderIdAndDayOfWeek(Long providerId, Integer dayOfWeek);

    /** §8.14: GET /staff/providers/{providerId}/availability-rules — every rule for a provider. */
    List<ProviderAvailabilityRule> findByProviderIdOrderByDayOfWeekAscStartTimeAsc(Long providerId);
}
