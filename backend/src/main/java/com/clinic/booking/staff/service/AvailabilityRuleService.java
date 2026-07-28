package com.clinic.booking.staff.service;

import com.clinic.booking.booking.domain.ProviderAvailabilityRule;
import com.clinic.booking.booking.repository.ProviderAvailabilityRuleRepository;
import com.clinic.booking.booking.repository.ProviderRepository;
import com.clinic.booking.common.exception.AvailabilityRuleNotFoundException;
import com.clinic.booking.common.exception.AvailabilityRuleOverlapException;
import com.clinic.booking.common.exception.InvalidTimeRangeException;
import com.clinic.booking.common.exception.MissingRequiredFieldException;
import com.clinic.booking.common.exception.ProviderNotFoundException;
import com.clinic.booking.common.exception.ValidationException;
import com.clinic.booking.staff.dto.AvailabilityRuleRequest;
import com.clinic.booking.staff.dto.AvailabilityRuleResponse;
import com.clinic.booking.staff.security.StaffUserPrincipal;
import com.clinic.booking.staff.domain.StaffUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implements the provider-availability-rule CRUD endpoints (PRD §8.14).
 * Non-overlapping validation (§19 #38) happens here, at the service layer,
 * before insert/update — the concrete mechanism the edge case describes.
 */
@Service
public class AvailabilityRuleService {

    private final ProviderAvailabilityRuleRepository ruleRepository;
    private final ProviderRepository providerRepository;

    public AvailabilityRuleService(
            ProviderAvailabilityRuleRepository ruleRepository, ProviderRepository providerRepository) {
        this.ruleRepository = ruleRepository;
        this.providerRepository = providerRepository;
    }

    @PreAuthorize("hasRole('STAFF') or hasRole('PROVIDER')")
    @Transactional(readOnly = true)
    public List<AvailabilityRuleResponse> list(Long providerId, StaffUserPrincipal principal) {
        checkProviderScope(providerId, principal);
        if (!providerRepository.existsById(providerId)) {
            throw new ProviderNotFoundException();
        }
        return ruleRepository.findByProviderIdOrderByDayOfWeekAscStartTimeAsc(providerId).stream()
                .map(AvailabilityRuleResponse::from)
                .toList();
    }

    @PreAuthorize("!hasRole('SYSADMIN') and hasRole('ADMIN')")
    @Transactional
    public AvailabilityRuleResponse create(Long providerId, AvailabilityRuleRequest request) {
        if (!providerRepository.existsById(providerId)) {
            throw new ProviderNotFoundException();
        }
        validate(request);
        checkOverlap(providerId, request, null);
        ProviderAvailabilityRule rule = new ProviderAvailabilityRule(
                providerId, request.dayOfWeek(), request.startTime(), request.endTime(), request.ruleType());
        ruleRepository.save(rule);
        return AvailabilityRuleResponse.from(rule);
    }

    @PreAuthorize("!hasRole('SYSADMIN') and hasRole('ADMIN')")
    @Transactional
    public AvailabilityRuleResponse update(Long id, AvailabilityRuleRequest request) {
        validate(request);
        ProviderAvailabilityRule rule = load(id);
        checkOverlap(rule.getProviderId(), request, id);
        rule.update(request.dayOfWeek(), request.startTime(), request.endTime(), request.ruleType());
        return AvailabilityRuleResponse.from(rule);
    }

    @PreAuthorize("!hasRole('SYSADMIN') and hasRole('ADMIN')")
    @Transactional
    public void delete(Long id) {
        ProviderAvailabilityRule rule = load(id);
        ruleRepository.delete(rule);
    }

    private ProviderAvailabilityRule load(Long id) {
        return ruleRepository.findById(id).orElseThrow(AvailabilityRuleNotFoundException::new);
    }

    private void checkProviderScope(Long providerId, StaffUserPrincipal principal) {
        if (principal.getRole() == StaffUser.Role.ROLE_PROVIDER && !principal.getProviderId().equals(providerId)) {
            throw new AccessDeniedException("Providers may only view their own availability rules.");
        }
    }

    private void checkOverlap(Long providerId, AvailabilityRuleRequest request, Long excludeId) {
        boolean overlaps = ruleRepository.findByProviderIdAndDayOfWeek(providerId, request.dayOfWeek()).stream()
                .filter(existing -> !existing.getId().equals(excludeId))
                .anyMatch(existing -> request.startTime().isBefore(existing.getEndTime())
                        && existing.getStartTime().isBefore(request.endTime()));
        if (overlaps) {
            throw new AvailabilityRuleOverlapException();
        }
    }

    private void validate(AvailabilityRuleRequest request) {
        if (request == null || request.dayOfWeek() == null) {
            throw new MissingRequiredFieldException("dayOfWeek");
        }
        if (request.startTime() == null) {
            throw new MissingRequiredFieldException("startTime");
        }
        if (request.endTime() == null) {
            throw new MissingRequiredFieldException("endTime");
        }
        if (request.ruleType() == null) {
            throw new MissingRequiredFieldException("ruleType");
        }
        if (request.dayOfWeek() < 0 || request.dayOfWeek() > 6) {
            throw new ValidationException("dayOfWeek", "must be between 0 and 6");
        }
        if (!request.startTime().isBefore(request.endTime())) {
            throw new InvalidTimeRangeException();
        }
    }
}
