package com.clinic.booking.staff.controller;

import com.clinic.booking.staff.dto.HolidayRequest;
import com.clinic.booking.staff.dto.HolidayResponse;
import com.clinic.booking.staff.service.ClinicHolidayService;
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

/** GET/POST/PUT/DELETE /api/v1/staff/holidays (PRD §8.16). */
@RestController
@RequestMapping("/api/v1/staff/holidays")
public class ClinicHolidayController {

    private final ClinicHolidayService clinicHolidayService;

    public ClinicHolidayController(ClinicHolidayService clinicHolidayService) {
        this.clinicHolidayService = clinicHolidayService;
    }

    @GetMapping
    public List<HolidayResponse> list() {
        return clinicHolidayService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HolidayResponse create(@RequestBody(required = false) HolidayRequest request) {
        return clinicHolidayService.create(request);
    }

    @PutMapping("/{id}")
    public HolidayResponse update(@PathVariable Long id, @RequestBody(required = false) HolidayRequest request) {
        return clinicHolidayService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        clinicHolidayService.delete(id);
    }
}
