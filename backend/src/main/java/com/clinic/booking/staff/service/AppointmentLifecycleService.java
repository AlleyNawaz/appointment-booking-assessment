package com.clinic.booking.staff.service;

import com.clinic.booking.audit.AuditLogWriter;
import com.clinic.booking.booking.domain.Appointment;
import com.clinic.booking.booking.repository.AppointmentRepository;
import com.clinic.booking.common.exception.AppointmentNotFoundException;
import com.clinic.booking.common.exception.StaleVersionException;
import com.clinic.booking.common.exception.ValidationException;
import com.clinic.booking.config.BookingProperties;
import com.clinic.booking.staff.domain.StaffUser;
import com.clinic.booking.staff.dto.AppointmentPageResponse;
import com.clinic.booking.staff.dto.StaffAppointmentResponse;
import com.clinic.booking.staff.security.StaffUserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implements the staff appointment console's list/approve/reject/complete
 * operations (PRD §8.9/§8.10). Method-level {@code @PreAuthorize} on every
 * method (§10), not the controller — read methods use the role hierarchy
 * (§2: {@code ROLE_SYSADMIN ⊇ ROLE_ADMIN ⊇ ROLE_STAFF}), mutating methods
 * enumerate roles literally and explicitly exclude {@code ROLE_SYSADMIN}
 * despite it sitting atop that hierarchy — {@code hasAnyRole}/{@code hasRole}
 * checks are hierarchy-aware, so a mutating check written the same way reads
 * are (e.g. {@code hasAnyRole('STAFF','ADMIN')}) would incorrectly let
 * {@code ROLE_SYSADMIN} inherit write access; the explicit
 * {@code !hasRole('SYSADMIN')} guard is what actually excludes it.
 */
@Service
public class AppointmentLifecycleService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Map<String, String> SORT_PROPERTIES =
            Map.of("startDateTime", "startDatetime", "createdAt", "createdAt", "status", "status");

    private final AppointmentRepository appointmentRepository;
    private final AuditLogWriter auditLogWriter;
    private final BookingProperties bookingProperties;

    public AppointmentLifecycleService(
            AppointmentRepository appointmentRepository,
            AuditLogWriter auditLogWriter,
            BookingProperties bookingProperties) {
        this.appointmentRepository = appointmentRepository;
        this.auditLogWriter = auditLogWriter;
        this.bookingProperties = bookingProperties;
    }

    /** §8.9: read hierarchy applies — {@code ROLE_PROVIDER} is scoped to its own {@code provider_id}. */
    @PreAuthorize("hasAnyRole('STAFF','PROVIDER')")
    @Transactional(readOnly = true)
    public AppointmentPageResponse list(
            Appointment.Status status, Long providerId, LocalDate from, LocalDate to, int page, int size,
            String sort, StaffUserPrincipal principal) {
        int clampedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Long effectiveProviderId =
                principal.getRole() == StaffUser.Role.ROLE_PROVIDER ? principal.getProviderId() : providerId;

        ZoneId clinicZone = ZoneId.of(bookingProperties.getClinicTimezone());
        Instant fromInstant = from == null ? null : from.atStartOfDay(clinicZone).toInstant();
        Instant toInstant = to == null ? null : to.plusDays(1).atStartOfDay(clinicZone).toInstant();

        PageRequest pageRequest = PageRequest.of(page, clampedSize, resolveSort(sort));
        Page<Appointment> result =
                appointmentRepository.search(status, effectiveProviderId, fromInstant, toInstant, pageRequest);

        List<StaffAppointmentResponse> content =
                result.getContent().stream().map(StaffAppointmentResponse::from).toList();
        return new AppointmentPageResponse(content, page, clampedSize, result.getTotalElements(), result.getTotalPages());
    }

    /** §8.10: {@code PENDING → CONFIRMED}. */
    @PreAuthorize("!hasRole('SYSADMIN') and (hasAnyRole('STAFF','ADMIN') or hasRole('PROVIDER'))")
    @Transactional
    public StaffAppointmentResponse approve(Long id, int ifMatchVersion, StaffUserPrincipal principal) {
        Appointment appointment = load(id);
        checkProviderScope(appointment, principal);
        checkTransition(appointment, ifMatchVersion, Appointment.Status.PENDING);
        appointment.approve();
        persist(appointment);
        auditLogWriter.write(
                appointment.getId(), Appointment.Status.PENDING, Appointment.Status.CONFIRMED,
                principal.getUsername(), null);
        return StaffAppointmentResponse.from(appointment);
    }

    /** §8.10: {@code PENDING → REJECTED}, reason required. */
    @PreAuthorize("!hasRole('SYSADMIN') and (hasAnyRole('STAFF','ADMIN') or hasRole('PROVIDER'))")
    @Transactional
    public StaffAppointmentResponse reject(Long id, String reason, int ifMatchVersion, StaffUserPrincipal principal) {
        Appointment appointment = load(id);
        checkProviderScope(appointment, principal);
        checkTransition(appointment, ifMatchVersion, Appointment.Status.PENDING);
        appointment.reject();
        persist(appointment);
        auditLogWriter.write(
                appointment.getId(), Appointment.Status.PENDING, Appointment.Status.REJECTED,
                principal.getUsername(), reason);
        return StaffAppointmentResponse.from(appointment);
    }

    /** §8.10: {@code CONFIRMED → COMPLETED}. */
    @PreAuthorize("!hasRole('SYSADMIN') and (hasAnyRole('STAFF','ADMIN') or hasRole('PROVIDER'))")
    @Transactional
    public StaffAppointmentResponse complete(Long id, int ifMatchVersion, StaffUserPrincipal principal) {
        Appointment appointment = load(id);
        checkProviderScope(appointment, principal);
        checkTransition(appointment, ifMatchVersion, Appointment.Status.CONFIRMED);
        appointment.complete();
        persist(appointment);
        auditLogWriter.write(
                appointment.getId(), Appointment.Status.CONFIRMED, Appointment.Status.COMPLETED,
                principal.getUsername(), null);
        return StaffAppointmentResponse.from(appointment);
    }

    private Appointment load(Long id) {
        return appointmentRepository.findById(id).orElseThrow(AppointmentNotFoundException::new);
    }

    /** §10.9/§19 #49: a provider may only act on its own {@code provider_id} — enforced server-side, not just hidden. */
    private void checkProviderScope(Appointment appointment, StaffUserPrincipal principal) {
        if (principal.getRole() == StaffUser.Role.ROLE_PROVIDER
                && !appointment.getProviderId().equals(principal.getProviderId())) {
            throw new AccessDeniedException("Providers may only act on their own appointments.");
        }
    }

    /**
     * §19 #15/#24: a version mismatch (genuine concurrent write) and an invalid transition
     * (current status isn't the one this endpoint requires) both surface as the same
     * {@code 409 STALE_VERSION} conflict — the diagram has no edge for the latter either way.
     */
    private void checkTransition(Appointment appointment, int ifMatchVersion, Appointment.Status expectedStatus) {
        if (appointment.getVersion() != ifMatchVersion || appointment.getStatus() != expectedStatus) {
            throw new StaleVersionException();
        }
    }

    /** §10/§12.12: a genuine race between the check above and this flush is Hibernate's own optimistic lock. */
    private void persist(Appointment appointment) {
        try {
            appointmentRepository.saveAndFlush(appointment);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new StaleVersionException();
        }
    }

    private Sort resolveSort(String sort) {
        String[] parts = sort.split(",", 2);
        String property = SORT_PROPERTIES.get(parts[0]);
        if (property == null) {
            throw new ValidationException("sort", "must be one of " + Set.copyOf(SORT_PROPERTIES.keySet()));
        }
        Sort.Direction direction =
                parts.length > 1 && "desc".equalsIgnoreCase(parts[1]) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, property);
    }
}
