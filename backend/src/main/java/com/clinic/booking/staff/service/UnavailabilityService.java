package com.clinic.booking.staff.service;

import com.clinic.booking.booking.domain.Appointment;
import com.clinic.booking.booking.domain.ProviderUnavailability;
import com.clinic.booking.booking.repository.AppointmentRepository;
import com.clinic.booking.booking.repository.ProviderRepository;
import com.clinic.booking.booking.repository.ProviderUnavailabilityRepository;
import com.clinic.booking.common.exception.InvalidTimeRangeException;
import com.clinic.booking.common.exception.MissingRequiredFieldException;
import com.clinic.booking.common.exception.ProviderNotFoundException;
import com.clinic.booking.common.exception.UnavailabilityNotFoundException;
import com.clinic.booking.common.exception.ValidationException;
import com.clinic.booking.staff.domain.StaffUser;
import com.clinic.booking.staff.dto.UnavailabilityRequest;
import com.clinic.booking.staff.dto.UnavailabilityResponse;
import com.clinic.booking.staff.security.StaffUserPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Implements the provider-unavailability CRUD endpoints (PRD §8.15/§12.2/§12.3).
 * {@code affectedAppointments} (§7.5's {@code needs_attention} surface) is
 * computed via the same overlap query already used by availability
 * computation (§8.4) — no second implementation of the overlap check.
 */
@Service
public class UnavailabilityService {

    private static final List<Appointment.Status> ACTIVE_STATUSES =
            List.of(Appointment.Status.PENDING, Appointment.Status.CONFIRMED);

    private final ProviderUnavailabilityRepository unavailabilityRepository;
    private final ProviderRepository providerRepository;
    private final AppointmentRepository appointmentRepository;

    public UnavailabilityService(
            ProviderUnavailabilityRepository unavailabilityRepository,
            ProviderRepository providerRepository,
            AppointmentRepository appointmentRepository) {
        this.unavailabilityRepository = unavailabilityRepository;
        this.providerRepository = providerRepository;
        this.appointmentRepository = appointmentRepository;
    }

    @PreAuthorize("hasRole('STAFF') or hasRole('PROVIDER')")
    @Transactional(readOnly = true)
    public List<UnavailabilityResponse> list(Long providerId, Instant from, Instant to, StaffUserPrincipal principal) {
        checkProviderScope(providerId, principal);
        if (!providerRepository.existsById(providerId)) {
            throw new ProviderNotFoundException();
        }
        List<ProviderUnavailability> rows = from != null && to != null
                ? unavailabilityRepository.findOverlapping(providerId, from, to)
                : unavailabilityRepository.findByProviderIdOrderByStartDatetimeAsc(providerId);
        return rows.stream().map(UnavailabilityResponse::from).toList();
    }

    @PreAuthorize("!hasRole('SYSADMIN') and (hasAnyRole('STAFF','ADMIN') or hasRole('PROVIDER'))")
    @Transactional
    public UnavailabilityResponse create(Long providerId, UnavailabilityRequest request, StaffUserPrincipal principal) {
        checkProviderScope(providerId, principal);
        if (!providerRepository.existsById(providerId)) {
            throw new ProviderNotFoundException();
        }
        validate(request);
        ProviderUnavailability unavailability = new ProviderUnavailability(
                providerId, request.startDatetime(), request.endDatetime(), request.reason(),
                principal.getUsername());
        unavailabilityRepository.save(unavailability);
        List<Appointment> affected = appointmentRepository.findActiveOverlapping(
                providerId, ACTIVE_STATUSES, request.startDatetime(), request.endDatetime());
        return UnavailabilityResponse.from(unavailability, affected);
    }

    @PreAuthorize("!hasRole('SYSADMIN') and (hasAnyRole('STAFF','ADMIN') or hasRole('PROVIDER'))")
    @Transactional
    public void delete(Long id, StaffUserPrincipal principal) {
        ProviderUnavailability unavailability = unavailabilityRepository.findById(id)
                .orElseThrow(UnavailabilityNotFoundException::new);
        checkProviderScope(unavailability.getProviderId(), principal);
        unavailabilityRepository.delete(unavailability);
    }

    private void checkProviderScope(Long providerId, StaffUserPrincipal principal) {
        if (principal.getRole() == StaffUser.Role.ROLE_PROVIDER && !principal.getProviderId().equals(providerId)) {
            throw new AccessDeniedException("Providers may only manage their own unavailability.");
        }
    }

    private void validate(UnavailabilityRequest request) {
        if (request == null || request.startDatetime() == null) {
            throw new MissingRequiredFieldException("startDatetime");
        }
        if (request.endDatetime() == null) {
            throw new MissingRequiredFieldException("endDatetime");
        }
        if (request.reason() == null || request.reason().isBlank()) {
            throw new MissingRequiredFieldException("reason");
        }
        if (request.reason().length() > 255) {
            throw new ValidationException("reason", "must be 1-255 characters");
        }
        if (!request.startDatetime().isBefore(request.endDatetime())) {
            throw new InvalidTimeRangeException();
        }
    }
}
