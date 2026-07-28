package com.clinic.booking.staff.controller;

import com.clinic.booking.staff.dto.AvailabilityRuleRequest;
import com.clinic.booking.staff.dto.AvailabilityRuleResponse;
import com.clinic.booking.staff.security.StaffUserPrincipal;
import com.clinic.booking.staff.service.AvailabilityRuleService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** GET/POST/PUT/DELETE for provider availability rules (PRD §8.14). */
@RestController
public class AvailabilityRuleController {

    private final AvailabilityRuleService availabilityRuleService;

    public AvailabilityRuleController(AvailabilityRuleService availabilityRuleService) {
        this.availabilityRuleService = availabilityRuleService;
    }

    @GetMapping("/api/v1/staff/providers/{providerId}/availability-rules")
    public List<AvailabilityRuleResponse> list(
            @PathVariable Long providerId, @AuthenticationPrincipal StaffUserPrincipal principal) {
        return availabilityRuleService.list(providerId, principal);
    }

    @PostMapping("/api/v1/staff/providers/{providerId}/availability-rules")
    @ResponseStatus(HttpStatus.CREATED)
    public AvailabilityRuleResponse create(
            @PathVariable Long providerId, @RequestBody(required = false) AvailabilityRuleRequest request) {
        return availabilityRuleService.create(providerId, request);
    }

    @PutMapping("/api/v1/staff/availability-rules/{id}")
    public AvailabilityRuleResponse update(
            @PathVariable Long id, @RequestBody(required = false) AvailabilityRuleRequest request) {
        return availabilityRuleService.update(id, request);
    }

    @DeleteMapping("/api/v1/staff/availability-rules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        availabilityRuleService.delete(id);
    }
}
