package com.clinic.booking.booking.controller;

import com.clinic.booking.booking.dto.AppointmentTypeResponse;
import com.clinic.booking.booking.service.AppointmentTypeService;
import com.clinic.booking.config.FeatureGate;
import com.clinic.booking.config.FeatureFlagService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * GET /api/v1/booking/appointment-types (PRD §8.2) — gated.
 */
@RestController
@RequestMapping("/api/v1/booking")
public class AppointmentTypeController {

    private final AppointmentTypeService appointmentTypeService;

    public AppointmentTypeController(AppointmentTypeService appointmentTypeService) {
        this.appointmentTypeService = appointmentTypeService;
    }

    @FeatureGate(FeatureFlagService.ENABLE_ONLINE_BOOKING)
    @GetMapping("/appointment-types")
    public List<AppointmentTypeResponse> getAppointmentTypes() {
        return appointmentTypeService.listActive();
    }
}
