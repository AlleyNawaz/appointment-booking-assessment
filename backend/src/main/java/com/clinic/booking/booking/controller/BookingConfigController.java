package com.clinic.booking.booking.controller;

import com.clinic.booking.booking.dto.ConfigResponse;
import com.clinic.booking.config.FeatureFlagService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/v1/booking/config (PRD §8.1) — public, never gated. This is how the
 * frontend discovers flag state (§6), so it cannot itself be gated.
 */
@RestController
@RequestMapping("/api/v1/booking")
public class BookingConfigController {

    private final FeatureFlagService featureFlagService;

    public BookingConfigController(FeatureFlagService featureFlagService) {
        this.featureFlagService = featureFlagService;
    }

    @GetMapping("/config")
    public ConfigResponse getConfig() {
        return new ConfigResponse(featureFlagService.isEnabled(FeatureFlagService.ENABLE_ONLINE_BOOKING));
    }
}
