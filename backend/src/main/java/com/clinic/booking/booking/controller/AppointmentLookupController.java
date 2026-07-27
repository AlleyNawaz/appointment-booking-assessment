package com.clinic.booking.booking.controller;

import com.clinic.booking.booking.dto.AppointmentDetailResponse;
import com.clinic.booking.booking.dto.CancelAppointmentRequest;
import com.clinic.booking.booking.service.AppointmentLookupService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET/DELETE /api/v1/booking/appointments/{confirmationToken} (PRD §8.7/§8.8)
 * — never gated: these service bookings that already exist and were made
 * when the flag was on (§6), so no {@code @FeatureGate} here.
 */
@RestController
@RequestMapping("/api/v1/booking/appointments")
public class AppointmentLookupController {

    private final AppointmentLookupService appointmentLookupService;

    public AppointmentLookupController(AppointmentLookupService appointmentLookupService) {
        this.appointmentLookupService = appointmentLookupService;
    }

    @GetMapping("/{*confirmationToken}")
    public AppointmentDetailResponse getAppointment(@PathVariable String confirmationToken) {
        return appointmentLookupService.getAppointmentDetail(stripLeadingSlash(confirmationToken));
    }

    @DeleteMapping("/{*confirmationToken}")
    @ResponseStatus(HttpStatus.OK)
    public AppointmentDetailResponse cancelAppointment(
            @PathVariable String confirmationToken,
            @RequestBody(required = false) CancelAppointmentRequest request) {
        String reason = request == null ? null : request.reason();
        return appointmentLookupService.cancelAppointment(stripLeadingSlash(confirmationToken), reason);
    }

    /**
     * §15.3: {@code {*confirmationToken}} (a "capture the rest" pattern) is what lets a
     * token containing "/" — which the plain single-segment {@code {confirmationToken}}
     * pattern never matched, falling through to Spring Boot's default error handling
     * instead of this controller — reach the same lookup and produce the same
     * {@code APPOINTMENT_NOT_FOUND} response as any other unknown token. The captured
     * value includes its own leading "/", which is stripped here before lookup.
     */
    private static String stripLeadingSlash(String confirmationToken) {
        return confirmationToken.startsWith("/") ? confirmationToken.substring(1) : confirmationToken;
    }
}
