package com.clinic.booking.staff.controller;

import com.clinic.booking.booking.domain.Appointment;
import com.clinic.booking.common.exception.MissingRequiredFieldException;
import com.clinic.booking.staff.dto.AppointmentPageResponse;
import com.clinic.booking.staff.dto.RejectRequest;
import com.clinic.booking.staff.dto.StaffAppointmentResponse;
import com.clinic.booking.staff.security.StaffUserPrincipal;
import com.clinic.booking.staff.service.AppointmentLifecycleService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** GET/POST /api/v1/staff/appointments/** (PRD §8.9/§8.10) — authenticated staff console. */
@RestController
@RequestMapping("/api/v1/staff/appointments")
public class StaffAppointmentController {

    private final AppointmentLifecycleService appointmentLifecycleService;

    public StaffAppointmentController(AppointmentLifecycleService appointmentLifecycleService) {
        this.appointmentLifecycleService = appointmentLifecycleService;
    }

    @GetMapping
    public AppointmentPageResponse list(
            @RequestParam(required = false) Appointment.Status status,
            @RequestParam(required = false) Long providerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "startDateTime,asc") String sort,
            @AuthenticationPrincipal StaffUserPrincipal principal) {
        return appointmentLifecycleService.list(status, providerId, from, to, page, size, sort, principal);
    }

    @PostMapping("/{id}/approve")
    public StaffAppointmentResponse approve(
            @PathVariable Long id,
            @RequestHeader(value = "If-Match", required = false) Integer ifMatch,
            @AuthenticationPrincipal StaffUserPrincipal principal) {
        requireIfMatch(ifMatch);
        return appointmentLifecycleService.approve(id, ifMatch, principal);
    }

    @PostMapping("/{id}/reject")
    public StaffAppointmentResponse reject(
            @PathVariable Long id,
            @RequestHeader(value = "If-Match", required = false) Integer ifMatch,
            @RequestBody(required = false) RejectRequest request,
            @AuthenticationPrincipal StaffUserPrincipal principal) {
        requireIfMatch(ifMatch);
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            throw new MissingRequiredFieldException("reason");
        }
        return appointmentLifecycleService.reject(id, request.reason(), ifMatch, principal);
    }

    @PostMapping("/{id}/complete")
    public StaffAppointmentResponse complete(
            @PathVariable Long id,
            @RequestHeader(value = "If-Match", required = false) Integer ifMatch,
            @AuthenticationPrincipal StaffUserPrincipal principal) {
        requireIfMatch(ifMatch);
        return appointmentLifecycleService.complete(id, ifMatch, principal);
    }

    private static void requireIfMatch(Integer ifMatch) {
        if (ifMatch == null) {
            throw new MissingRequiredFieldException("If-Match");
        }
    }
}
