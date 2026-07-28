package com.clinic.booking.staff.controller;

import com.clinic.booking.staff.dto.UnavailabilityRequest;
import com.clinic.booking.staff.dto.UnavailabilityResponse;
import com.clinic.booking.staff.security.StaffUserPrincipal;
import com.clinic.booking.staff.service.UnavailabilityService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** GET/POST/DELETE for provider unavailability (PRD §8.15). */
@RestController
public class UnavailabilityController {

    private final UnavailabilityService unavailabilityService;

    public UnavailabilityController(UnavailabilityService unavailabilityService) {
        this.unavailabilityService = unavailabilityService;
    }

    @GetMapping("/api/v1/staff/providers/{providerId}/unavailability")
    public List<UnavailabilityResponse> list(
            @PathVariable Long providerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @AuthenticationPrincipal StaffUserPrincipal principal) {
        return unavailabilityService.list(providerId, from, to, principal);
    }

    @PostMapping("/api/v1/staff/providers/{providerId}/unavailability")
    @ResponseStatus(HttpStatus.CREATED)
    public UnavailabilityResponse create(
            @PathVariable Long providerId,
            @RequestBody(required = false) UnavailabilityRequest request,
            @AuthenticationPrincipal StaffUserPrincipal principal) {
        return unavailabilityService.create(providerId, request, principal);
    }

    @DeleteMapping("/api/v1/staff/unavailability/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal StaffUserPrincipal principal) {
        unavailabilityService.delete(id, principal);
    }
}
