package com.clinic.booking.staff.controller;

import com.clinic.booking.staff.dto.AppointmentTypeAdminResponse;
import com.clinic.booking.staff.dto.AppointmentTypeRequest;
import com.clinic.booking.staff.service.AppointmentTypeAdminService;
import org.springframework.http.HttpStatus;
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

/** GET/POST/PUT/DELETE /api/v1/staff/appointment-types (PRD §8.12). */
@RestController
@RequestMapping("/api/v1/staff/appointment-types")
public class AppointmentTypeAdminController {

    private final AppointmentTypeAdminService appointmentTypeAdminService;

    public AppointmentTypeAdminController(AppointmentTypeAdminService appointmentTypeAdminService) {
        this.appointmentTypeAdminService = appointmentTypeAdminService;
    }

    @GetMapping
    public List<AppointmentTypeAdminResponse> list() {
        return appointmentTypeAdminService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentTypeAdminResponse create(@RequestBody(required = false) AppointmentTypeRequest request) {
        return appointmentTypeAdminService.create(request);
    }

    @PutMapping("/{id}")
    public AppointmentTypeAdminResponse update(
            @PathVariable Long id, @RequestBody(required = false) AppointmentTypeRequest request) {
        return appointmentTypeAdminService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public AppointmentTypeAdminResponse deactivate(@PathVariable Long id) {
        return appointmentTypeAdminService.deactivate(id);
    }
}
