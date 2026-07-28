package com.clinic.booking.staff.controller;

import com.clinic.booking.staff.dto.FeatureFlagRequest;
import com.clinic.booking.staff.dto.FeatureFlagResponse;
import com.clinic.booking.staff.security.StaffUserPrincipal;
import com.clinic.booking.staff.service.FeatureFlagAdminService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET/PUT /api/v1/staff/feature-flags/{flagName} (PRD §8.17). */
@RestController
@RequestMapping("/api/v1/staff/feature-flags")
public class FeatureFlagAdminController {

    private final FeatureFlagAdminService featureFlagAdminService;

    public FeatureFlagAdminController(FeatureFlagAdminService featureFlagAdminService) {
        this.featureFlagAdminService = featureFlagAdminService;
    }

    @GetMapping("/{flagName}")
    public FeatureFlagResponse get(@PathVariable String flagName) {
        return featureFlagAdminService.get(flagName);
    }

    @PutMapping("/{flagName}")
    public FeatureFlagResponse update(
            @PathVariable String flagName,
            @RequestBody(required = false) FeatureFlagRequest request,
            @AuthenticationPrincipal StaffUserPrincipal principal) {
        return featureFlagAdminService.update(flagName, request, principal);
    }
}
