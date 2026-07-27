package com.clinic.booking.booking.controller;

import com.clinic.booking.booking.dto.ProviderResponse;
import com.clinic.booking.booking.service.ProviderService;
import com.clinic.booking.config.FeatureGate;
import com.clinic.booking.config.FeatureFlagService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * GET /api/v1/booking/providers?appointmentTypeId= (PRD §8.3) — gated.
 * {@code appointmentTypeId} is required; Spring MVC rejects a missing required
 * {@code @RequestParam} with {@code MissingServletRequestParameterException}
 * before this method runs, which {@code GlobalExceptionHandler} maps to
 * {@code 400 VALIDATION_ERROR} — exactly what §8.3 specifies.
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
    public List<ProviderResponse> getProviders(@RequestParam Long appointmentTypeId) {
        return providerService.listByAppointmentType(appointmentTypeId);
    }
}
