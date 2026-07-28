package com.clinic.booking.staff.controller;

import com.clinic.booking.staff.dto.ProviderAdminResponse;
import com.clinic.booking.staff.dto.ProviderAppointmentTypesRequest;
import com.clinic.booking.staff.dto.ProviderRequest;
import com.clinic.booking.staff.service.ProviderAdminService;
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

/** GET/POST/PUT/DELETE /api/v1/staff/providers (PRD §8.13). */
@RestController
@RequestMapping("/api/v1/staff/providers")
public class ProviderAdminController {

    private final ProviderAdminService providerAdminService;

    public ProviderAdminController(ProviderAdminService providerAdminService) {
        this.providerAdminService = providerAdminService;
    }

    @GetMapping
    public List<ProviderAdminResponse> list() {
        return providerAdminService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProviderAdminResponse create(@RequestBody(required = false) ProviderRequest request) {
        return providerAdminService.create(request);
    }

    @PutMapping("/{id}")
    public ProviderAdminResponse update(@PathVariable Long id, @RequestBody(required = false) ProviderRequest request) {
        return providerAdminService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ProviderAdminResponse softDelete(@PathVariable Long id) {
        return providerAdminService.softDelete(id);
    }

    @PutMapping("/{id}/appointment-types")
    public ProviderAdminResponse replaceAppointmentTypes(
            @PathVariable Long id, @RequestBody(required = false) ProviderAppointmentTypesRequest request) {
        return providerAdminService.replaceAppointmentTypes(id, request == null ? null : request.appointmentTypeIds());
    }
}
