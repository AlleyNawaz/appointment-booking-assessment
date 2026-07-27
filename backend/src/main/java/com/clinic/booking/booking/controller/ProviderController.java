package com.clinic.booking.booking.controller;

import com.clinic.booking.booking.dto.ProviderResponse;
import com.clinic.booking.booking.service.ProviderService;
import com.clinic.booking.config.FeatureGate;
import com.clinic.booking.config.FeatureFlagService;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * GET /api/v1/booking/providers?appointmentTypeId= (PRD §8.3) — gated.
 *
 * <p>{@code appointmentTypeId} is declared optional at the Spring MVC binding
 * layer and checked explicitly in the method body instead of via a required
 * {@code @RequestParam}. A required {@code @RequestParam} is rejected by Spring
 * during argument resolution, which runs before {@code @FeatureGate}'s AOP
 * advice ever fires (the advice only wraps the method invocation) — letting a
 * request-shape error leak ahead of the flag check and violating §6's "checks
 * ... as the first statement before any other validation." Checking presence
 * here, after the aspect has already run, guarantees the flag check always
 * fires first, while still throwing the exact exception type
 * ({@link MissingServletRequestParameterException}) that {@code
 * GlobalExceptionHandler} already maps to {@code 400 VALIDATION_ERROR} —
 * so the response shape for a missing parameter is unchanged.
 */
@RestController
@RequestMapping("/api/v1/booking")
public class ProviderController {

    private final ProviderService providerService;

    public ProviderController(ProviderService providerService) {
        this.providerService = providerService;
    }

    @FeatureGate(FeatureFlagService.ENABLE_ONLINE_BOOKING)
    @GetMapping("/providers")
    public List<ProviderResponse> getProviders(@RequestParam(required = false) Long appointmentTypeId)
            throws MissingServletRequestParameterException {
        if (appointmentTypeId == null) {
            throw new MissingServletRequestParameterException("appointmentTypeId", "Long");
        }
        return providerService.listByAppointmentType(appointmentTypeId);
    }
}
