package com.clinic.booking.staff.service;

import com.clinic.booking.common.exception.FeatureFlagNotFoundException;
import com.clinic.booking.common.exception.MissingRequiredFieldException;
import com.clinic.booking.config.FeatureFlag;
import com.clinic.booking.config.FeatureFlagRepository;
import com.clinic.booking.config.FeatureFlagService;
import com.clinic.booking.staff.dto.FeatureFlagRequest;
import com.clinic.booking.staff.dto.FeatureFlagResponse;
import com.clinic.booking.staff.security.StaffUserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements the feature-flag GET/PUT endpoints (PRD §8.17). {@code ROLE_SYSADMIN} is granted
 * write access here — the one named exception to its read-only scope (§2) — so, unlike every
 * other mutating method in this milestone, the role check is deliberately hierarchy-based
 * ({@code hasRole('ADMIN')} alone, which already includes {@code ROLE_SYSADMIN} via §2's read
 * hierarchy) rather than excluding it.
 */
@Service
public class FeatureFlagAdminService {

    private final FeatureFlagRepository featureFlagRepository;
    private final FeatureFlagService featureFlagService;

    public FeatureFlagAdminService(FeatureFlagRepository featureFlagRepository, FeatureFlagService featureFlagService) {
        this.featureFlagRepository = featureFlagRepository;
        this.featureFlagService = featureFlagService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public FeatureFlagResponse get(String flagName) {
        return FeatureFlagResponse.from(load(flagName));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public FeatureFlagResponse update(String flagName, FeatureFlagRequest request, StaffUserPrincipal principal) {
        if (request == null || request.isEnabled() == null) {
            throw new MissingRequiredFieldException("isEnabled");
        }
        FeatureFlag flag = load(flagName);
        flag.update(request.isEnabled(), principal.getUsername());
        featureFlagService.evict(flagName);
        return FeatureFlagResponse.from(flag);
    }

    private FeatureFlag load(String flagName) {
        return featureFlagRepository.findById(flagName).orElseThrow(FeatureFlagNotFoundException::new);
    }
}
