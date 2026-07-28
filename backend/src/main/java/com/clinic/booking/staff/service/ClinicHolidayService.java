package com.clinic.booking.staff.service;

import com.clinic.booking.booking.domain.ClinicHoliday;
import com.clinic.booking.booking.repository.ClinicHolidayRepository;
import com.clinic.booking.common.exception.HolidayDateExistsException;
import com.clinic.booking.common.exception.HolidayNotFoundException;
import com.clinic.booking.common.exception.MissingRequiredFieldException;
import com.clinic.booking.common.exception.ValidationException;
import com.clinic.booking.staff.dto.HolidayRequest;
import com.clinic.booking.staff.dto.HolidayResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Implements the clinic-holiday CRUD endpoints (PRD §8.16). Past dates are explicitly permitted (§19 #50). */
@Service
public class ClinicHolidayService {

    private final ClinicHolidayRepository holidayRepository;

    public ClinicHolidayService(ClinicHolidayRepository holidayRepository) {
        this.holidayRepository = holidayRepository;
    }

    @PreAuthorize("hasRole('STAFF')")
    @Transactional(readOnly = true)
    public List<HolidayResponse> list() {
        return holidayRepository.findAllByOrderByHolidayDateAsc().stream().map(HolidayResponse::from).toList();
    }

    @PreAuthorize("!hasRole('SYSADMIN') and hasRole('ADMIN')")
    @Transactional
    public HolidayResponse create(HolidayRequest request) {
        validate(request);
        if (holidayRepository.existsByHolidayDate(request.holidayDate())) {
            throw new HolidayDateExistsException();
        }
        ClinicHoliday holiday = new ClinicHoliday(
                request.holidayDate(), request.name(), Boolean.TRUE.equals(request.isRecurringAnnually()));
        holidayRepository.save(holiday);
        return HolidayResponse.from(holiday);
    }

    @PreAuthorize("!hasRole('SYSADMIN') and hasRole('ADMIN')")
    @Transactional
    public HolidayResponse update(Long id, HolidayRequest request) {
        validate(request);
        ClinicHoliday holiday = load(id);
        if (holidayRepository.existsByHolidayDateAndIdNot(request.holidayDate(), id)) {
            throw new HolidayDateExistsException();
        }
        holiday.update(request.holidayDate(), request.name(), Boolean.TRUE.equals(request.isRecurringAnnually()));
        return HolidayResponse.from(holiday);
    }

    @PreAuthorize("!hasRole('SYSADMIN') and hasRole('ADMIN')")
    @Transactional
    public void delete(Long id) {
        holidayRepository.delete(load(id));
    }

    private ClinicHoliday load(Long id) {
        return holidayRepository.findById(id).orElseThrow(HolidayNotFoundException::new);
    }

    private void validate(HolidayRequest request) {
        if (request == null || request.holidayDate() == null) {
            throw new MissingRequiredFieldException("holidayDate");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new MissingRequiredFieldException("name");
        }
        if (request.name().length() > 150) {
            throw new ValidationException("name", "must be 1-150 characters");
        }
    }
}
