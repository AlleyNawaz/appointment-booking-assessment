package com.clinic.booking.booking.controller;

import com.clinic.booking.booking.dto.AvailabilityResponse;
import com.clinic.booking.booking.service.AvailabilityService;
import com.clinic.booking.config.FeatureGate;
import com.clinic.booking.config.FeatureFlagService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * GET /api/v1/booking/availability?providerId=&appointmentTypeId=&date= (PRD
 * §8.4) — gated.
 *
 * <p>As with {@code ProviderController} (§8.3), every parameter is declared
 * optional at the Spring MVC binding layer and checked explicitly in the
 * method body. A required {@code @RequestParam} is rejected by Spring during
 * argument resolution, which runs before {@code @FeatureGate}'s AOP advice
 * ever fires — letting a request-shape error leak ahead of the flag check
 * and violating §6's "checks ... as the first statement before any other
 * validation." Checking presence here, after the aspect has already run,
 * guarantees the flag check always fires first.
 */
@RestController
@RequestMapping("/api/v1/booking")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @FeatureGate(FeatureFlagService.ENABLE_ONLINE_BOOKING)
    @GetMapping("/availability")
    public AvailabilityResponse getAvailability(
            @RequestParam(required = false) Long providerId,
            @RequestParam(required = false) Long appointmentTypeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date)
            throws MissingServletRequestParameterException {
        if (providerId == null) {
            throw new MissingServletRequestParameterException("providerId", "Long");
        }
        if (appointmentTypeId == null) {
            throw new MissingServletRequestParameterException("appointmentTypeId", "Long");
        }
        if (date == null) {
            throw new MissingServletRequestParameterException("date", "LocalDate");
        }
        return availabilityService.computeAvailableSlots(providerId, appointmentTypeId, date);
    }
}
