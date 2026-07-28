package com.clinic.booking.staff.service;

import com.clinic.booking.booking.domain.AppointmentType;
import com.clinic.booking.booking.repository.AppointmentTypeRepository;
import com.clinic.booking.common.exception.AppointmentTypeCodeExistsException;
import com.clinic.booking.common.exception.AppointmentTypeNotFoundException;
import com.clinic.booking.common.exception.MissingRequiredFieldException;
import com.clinic.booking.common.exception.ValidationException;
import com.clinic.booking.staff.dto.AppointmentTypeAdminResponse;
import com.clinic.booking.staff.dto.AppointmentTypeRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

/** Implements the appointment-type CRUD endpoints (PRD §8.12). */
@Service
public class AppointmentTypeAdminService {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,49}$");

    private final AppointmentTypeRepository appointmentTypeRepository;

    public AppointmentTypeAdminService(AppointmentTypeRepository appointmentTypeRepository) {
        this.appointmentTypeRepository = appointmentTypeRepository;
    }

    @PreAuthorize("hasRole('STAFF')")
    @Transactional(readOnly = true)
    public List<AppointmentTypeAdminResponse> list() {
        return appointmentTypeRepository.findAllByOrderById().stream().map(AppointmentTypeAdminResponse::from).toList();
    }

    @PreAuthorize("!hasRole('SYSADMIN') and hasRole('ADMIN')")
    @Transactional
    public AppointmentTypeAdminResponse create(AppointmentTypeRequest request) {
        validate(request);
        if (appointmentTypeRepository.existsByCode(request.code())) {
            throw new AppointmentTypeCodeExistsException();
        }
        AppointmentType type = new AppointmentType(
                request.code(), request.displayName(), request.durationMinutes(), request.bufferMinutes(),
                Boolean.TRUE.equals(request.requiresApproval()), request.isActive() == null || request.isActive());
        appointmentTypeRepository.save(type);
        return AppointmentTypeAdminResponse.from(type);
    }

    @PreAuthorize("!hasRole('SYSADMIN') and hasRole('ADMIN')")
    @Transactional
    public AppointmentTypeAdminResponse update(Long id, AppointmentTypeRequest request) {
        validate(request);
        AppointmentType type = load(id);
        if (appointmentTypeRepository.existsByCodeAndIdNot(request.code(), id)) {
            throw new AppointmentTypeCodeExistsException();
        }
        type.update(
                request.code(), request.displayName(), request.durationMinutes(), request.bufferMinutes(),
                Boolean.TRUE.equals(request.requiresApproval()), request.isActive() == null || request.isActive());
        return AppointmentTypeAdminResponse.from(type);
    }

    @PreAuthorize("!hasRole('SYSADMIN') and hasRole('ADMIN')")
    @Transactional
    public AppointmentTypeAdminResponse deactivate(Long id) {
        AppointmentType type = load(id);
        type.deactivate();
        return AppointmentTypeAdminResponse.from(type);
    }

    private AppointmentType load(Long id) {
        return appointmentTypeRepository.findById(id).orElseThrow(AppointmentTypeNotFoundException::new);
    }

    private void validate(AppointmentTypeRequest request) {
        if (request == null || request.code() == null || request.code().isBlank()) {
            throw new MissingRequiredFieldException("code");
        }
        if (request.displayName() == null || request.displayName().isBlank()) {
            throw new MissingRequiredFieldException("displayName");
        }
        if (request.durationMinutes() == null) {
            throw new MissingRequiredFieldException("durationMinutes");
        }
        if (request.bufferMinutes() == null) {
            throw new MissingRequiredFieldException("bufferMinutes");
        }
        if (!CODE_PATTERN.matcher(request.code()).matches()) {
            throw new ValidationException("code", "must match ^[A-Z][A-Z0-9_]{1,49}$");
        }
        if (request.displayName().length() > 150) {
            throw new ValidationException("displayName", "must be 1-150 characters");
        }
        if (request.durationMinutes() < 5 || request.durationMinutes() > 480) {
            throw new ValidationException("durationMinutes", "must be between 5 and 480");
        }
        if (request.bufferMinutes() < 0 || request.bufferMinutes() > 120) {
            throw new ValidationException("bufferMinutes", "must be between 0 and 120");
        }
    }
}
