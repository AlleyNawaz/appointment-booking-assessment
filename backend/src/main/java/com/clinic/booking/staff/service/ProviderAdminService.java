package com.clinic.booking.staff.service;

import com.clinic.booking.booking.domain.AppointmentType;
import com.clinic.booking.booking.domain.Provider;
import com.clinic.booking.booking.repository.AppointmentTypeRepository;
import com.clinic.booking.booking.repository.ProviderRepository;
import com.clinic.booking.common.exception.InvalidAppointmentTypeReferenceException;
import com.clinic.booking.common.exception.InvalidTimezoneException;
import com.clinic.booking.common.exception.MissingRequiredFieldException;
import com.clinic.booking.common.exception.ProviderEmailExistsException;
import com.clinic.booking.common.exception.ProviderNotFoundException;
import com.clinic.booking.common.exception.ValidationException;
import com.clinic.booking.staff.dto.ProviderAdminResponse;
import com.clinic.booking.staff.dto.ProviderRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Implements the provider CRUD endpoints (PRD §8.13). */
@Service
public class ProviderAdminService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final ProviderRepository providerRepository;
    private final AppointmentTypeRepository appointmentTypeRepository;

    public ProviderAdminService(ProviderRepository providerRepository, AppointmentTypeRepository appointmentTypeRepository) {
        this.providerRepository = providerRepository;
        this.appointmentTypeRepository = appointmentTypeRepository;
    }

    @PreAuthorize("hasRole('STAFF')")
    @Transactional(readOnly = true)
    public List<ProviderAdminResponse> list() {
        return providerRepository.findAllByOrderById().stream().map(ProviderAdminResponse::from).toList();
    }

    @PreAuthorize("!hasRole('SYSADMIN') and hasRole('ADMIN')")
    @Transactional
    public ProviderAdminResponse create(ProviderRequest request) {
        validate(request);
        if (providerRepository.existsByEmail(request.email())) {
            throw new ProviderEmailExistsException();
        }
        Provider provider = new Provider(
                request.firstName(), request.lastName(), request.specialty(), request.email(), request.timezone(),
                request.isActive() == null || request.isActive());
        providerRepository.save(provider);
        return ProviderAdminResponse.from(provider);
    }

    @PreAuthorize("!hasRole('SYSADMIN') and hasRole('ADMIN')")
    @Transactional
    public ProviderAdminResponse update(Long id, ProviderRequest request) {
        validate(request);
        Provider provider = load(id);
        if (providerRepository.existsByEmailAndIdNot(request.email(), id)) {
            throw new ProviderEmailExistsException();
        }
        provider.update(
                request.firstName(), request.lastName(), request.specialty(), request.email(), request.timezone(),
                request.isActive() == null || request.isActive());
        return ProviderAdminResponse.from(provider);
    }

    @PreAuthorize("!hasRole('SYSADMIN') and hasRole('ADMIN')")
    @Transactional
    public ProviderAdminResponse softDelete(Long id) {
        Provider provider = load(id);
        provider.softDelete();
        return ProviderAdminResponse.from(provider);
    }

    @PreAuthorize("!hasRole('SYSADMIN') and hasRole('ADMIN')")
    @Transactional
    public ProviderAdminResponse replaceAppointmentTypes(Long id, List<Long> appointmentTypeIds) {
        Provider provider = load(id);
        if (appointmentTypeIds == null) {
            throw new MissingRequiredFieldException("appointmentTypeIds");
        }
        Set<AppointmentType> types = new LinkedHashSet<>();
        for (Long typeId : appointmentTypeIds) {
            types.add(appointmentTypeRepository.findById(typeId)
                    .orElseThrow(InvalidAppointmentTypeReferenceException::new));
        }
        provider.replaceAppointmentTypes(types);
        return ProviderAdminResponse.from(provider);
    }

    private Provider load(Long id) {
        return providerRepository.findById(id).orElseThrow(ProviderNotFoundException::new);
    }

    private void validate(ProviderRequest request) {
        if (request == null || request.firstName() == null || request.firstName().isBlank()) {
            throw new MissingRequiredFieldException("firstName");
        }
        if (request.lastName() == null || request.lastName().isBlank()) {
            throw new MissingRequiredFieldException("lastName");
        }
        if (request.specialty() == null || request.specialty().isBlank()) {
            throw new MissingRequiredFieldException("specialty");
        }
        if (request.email() == null || request.email().isBlank()) {
            throw new MissingRequiredFieldException("email");
        }
        if (request.timezone() == null || request.timezone().isBlank()) {
            throw new MissingRequiredFieldException("timezone");
        }
        if (request.firstName().length() > 100) {
            throw new ValidationException("firstName", "must be 1-100 characters");
        }
        if (request.lastName().length() > 100) {
            throw new ValidationException("lastName", "must be 1-100 characters");
        }
        if (request.specialty().length() > 150) {
            throw new ValidationException("specialty", "must be 1-150 characters");
        }
        if (request.email().length() > 254 || !EMAIL_PATTERN.matcher(request.email()).matches()) {
            throw new ValidationException("email", "must be a valid email address");
        }
        try {
            ZoneId.of(request.timezone());
        } catch (DateTimeException ex) {
            throw new InvalidTimezoneException();
        }
    }
}
