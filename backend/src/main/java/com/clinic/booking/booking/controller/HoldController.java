package com.clinic.booking.booking.controller;

import com.clinic.booking.booking.dto.HoldRequest;
import com.clinic.booking.booking.dto.HoldResponse;
import com.clinic.booking.booking.service.HoldService;
import com.clinic.booking.common.exception.MissingRequiredFieldException;
import com.clinic.booking.config.FeatureFlagService;
import com.clinic.booking.config.FeatureGate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/v1/booking/holds (PRD §8.5) — gated.
 *
 * <p>As with {@code ProviderController}/{@code AvailabilityController}, the
 * request body carries no {@code @Valid}/{@code @NotNull} bean-validation
 * annotations — those would be enforced during Spring MVC argument
 * resolution, which runs before {@code @FeatureGate}'s AOP advice ever fires.
 * Presence is checked explicitly here, after the aspect has already run, so
 * the flag check always fires first (§6).
 *
 * <p>The body itself is {@code @RequestBody(required = false)} for the same
 * reason: a plain {@code @RequestBody} throws before the method is ever
 * invoked (and therefore before {@code @FeatureGate} fires) when no body is
 * sent at all. Making it optional lets a missing body reach this null check
 * instead, so a disabled flag still wins the race.
 */
@RestController
@RequestMapping("/api/v1/booking")
public class HoldController {

    private final HoldService holdService;

    public HoldController(HoldService holdService) {
        this.holdService = holdService;
    }

    @FeatureGate(FeatureFlagService.ENABLE_ONLINE_BOOKING)
    @PostMapping("/holds")
    @ResponseStatus(HttpStatus.CREATED)
    public HoldResponse createHold(@RequestBody(required = false) HoldRequest request) {
        if (request == null) {
            throw new MissingRequiredFieldException("request body");
        }
        if (request.providerId() == null) {
            throw new MissingRequiredFieldException("providerId");
        }
        if (request.appointmentTypeId() == null) {
            throw new MissingRequiredFieldException("appointmentTypeId");
        }
        if (request.startDateTime() == null) {
            throw new MissingRequiredFieldException("startDateTime");
        }
        return holdService.createHold(request.providerId(), request.appointmentTypeId(), request.startDateTime());
    }
}
