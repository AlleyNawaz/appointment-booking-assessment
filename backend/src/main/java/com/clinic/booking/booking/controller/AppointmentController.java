package com.clinic.booking.booking.controller;

import com.clinic.booking.booking.dto.AppointmentResponse;
import com.clinic.booking.booking.dto.CreateAppointmentRequest;
import com.clinic.booking.booking.service.BookingService;
import com.clinic.booking.common.exception.MissingRequiredFieldException;
import com.clinic.booking.config.FeatureFlagService;
import com.clinic.booking.config.FeatureGate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/v1/booking/appointments (PRD §8.6) — gated.
 *
 * <p>Same ordering-safe pattern as {@code HoldController} (Milestone 4): the
 * {@code Idempotency-Key} header and the request body are both declared
 * optional so neither throws during Spring MVC argument resolution — which
 * runs before {@code @FeatureGate}'s AOP advice — and presence is checked
 * explicitly in the method body instead, after the flag check has already run.
 */
@RestController
@RequestMapping("/api/v1/booking")
public class AppointmentController {

    private final BookingService bookingService;

    public AppointmentController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @FeatureGate(FeatureFlagService.ENABLE_ONLINE_BOOKING)
    @PostMapping("/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse createAppointment(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) CreateAppointmentRequest request) {
        if (idempotencyKey == null) {
            throw new MissingRequiredFieldException("Idempotency-Key");
        }
        if (request == null) {
            throw new MissingRequiredFieldException("request body");
        }
        if (request.holdToken() == null) {
            throw new MissingRequiredFieldException("holdToken");
        }
        if (request.patientFullName() == null) {
            throw new MissingRequiredFieldException("patientFullName");
        }
        if (request.patientEmail() == null) {
            throw new MissingRequiredFieldException("patientEmail");
        }
        if (request.patientPhone() == null) {
            throw new MissingRequiredFieldException("patientPhone");
        }
        return bookingService.createAppointment(request, idempotencyKey);
    }
}
