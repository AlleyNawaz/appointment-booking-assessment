package com.clinic.booking.booking.service;

import com.clinic.booking.audit.AuditLogWriter;
import com.clinic.booking.booking.domain.Appointment;
import com.clinic.booking.booking.domain.AppointmentType;
import com.clinic.booking.booking.domain.Provider;
import com.clinic.booking.booking.dto.AppointmentDetailResponse;
import com.clinic.booking.booking.repository.AppointmentRepository;
import com.clinic.booking.booking.repository.AppointmentTypeRepository;
import com.clinic.booking.booking.repository.ProviderRepository;
import com.clinic.booking.common.exception.AppointmentNotFoundException;
import com.clinic.booking.common.exception.CancellationWindowExpiredException;
import com.clinic.booking.config.BookingProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Implements the never-gated, token-based patient self-service endpoints
 * (PRD §8.7/§8.8) — lookup and cancellation of an appointment that already
 * exists. Independent of {@link BookingService}: it neither creates
 * appointments nor shares any of that service's booking-creation state.
 */
@Service
public class AppointmentLookupService {

    private final AppointmentRepository appointmentRepository;
    private final ProviderRepository providerRepository;
    private final AppointmentTypeRepository appointmentTypeRepository;
    private final AuditLogWriter auditLogWriter;
    private final BookingProperties bookingProperties;

    public AppointmentLookupService(
            AppointmentRepository appointmentRepository,
            ProviderRepository providerRepository,
            AppointmentTypeRepository appointmentTypeRepository,
            AuditLogWriter auditLogWriter,
            BookingProperties bookingProperties) {
        this.appointmentRepository = appointmentRepository;
        this.providerRepository = providerRepository;
        this.appointmentTypeRepository = appointmentTypeRepository;
        this.auditLogWriter = auditLogWriter;
        this.bookingProperties = bookingProperties;
    }

    @Transactional(readOnly = true)
    public AppointmentDetailResponse getAppointmentDetail(String confirmationToken) {
        Appointment appointment = findByToken(confirmationToken);
        return toResponse(appointment);
    }

    @Transactional
    public AppointmentDetailResponse cancelAppointment(String confirmationToken, String reason) {
        Appointment appointment = findByToken(confirmationToken);

        // §12.7: no diagram edge exists for a patient cancelling anything but a
        // CONFIRMED appointment; §12.6's cutoff check only makes sense for one.
        if (appointment.getStatus() != Appointment.Status.CONFIRMED || !isEligibleForCancellation(appointment)) {
            throw new CancellationWindowExpiredException(bookingProperties.getClinicPhoneNumber());
        }

        Appointment.Status previousStatus = appointment.getStatus();
        appointment.cancel(reason);
        auditLogWriter.write(appointment.getId(), previousStatus, Appointment.Status.CANCELLED,
                "PATIENT_SELF_SERVICE", reason);

        return toResponse(appointment);
    }

    private Appointment findByToken(String confirmationToken) {
        return appointmentRepository.findByConfirmationToken(confirmationToken)
                .orElseThrow(AppointmentNotFoundException::new);
    }

    private boolean isEligibleForCancellation(Appointment appointment) {
        Instant cutoff = Instant.now().plus(bookingProperties.getCancellationCutoffHours(), ChronoUnit.HOURS);
        return appointment.getStartDatetime().isAfter(cutoff);
    }

    private AppointmentDetailResponse toResponse(Appointment appointment) {
        Provider provider = providerRepository.findById(appointment.getProviderId()).orElseThrow();
        AppointmentType appointmentType =
                appointmentTypeRepository.findById(appointment.getAppointmentTypeId()).orElseThrow();
        String providerName = provider.getFirstName() + " " + provider.getLastName();
        boolean cancellationEligible =
                appointment.getStatus() == Appointment.Status.CONFIRMED && isEligibleForCancellation(appointment);

        return new AppointmentDetailResponse(appointment.getConfirmationToken(), providerName,
                appointmentType.getDisplayName(), appointment.getStartDatetime(), appointment.getStatus(),
                cancellationEligible);
    }
}
