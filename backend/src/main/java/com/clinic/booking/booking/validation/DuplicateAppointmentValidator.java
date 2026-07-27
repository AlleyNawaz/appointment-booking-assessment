package com.clinic.booking.booking.validation;

import com.clinic.booking.booking.domain.Appointment;
import com.clinic.booking.booking.repository.AppointmentRepository;
import com.clinic.booking.common.exception.DuplicateAppointmentException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * §11.7: the same patient identity ({@code lower(email) + phone}) cannot book
 * the same provider at an overlapping time while already holding an active
 * appointment there.
 */
@Component
public class DuplicateAppointmentValidator {

    private static final List<Appointment.Status> ACTIVE_STATUSES =
            List.of(Appointment.Status.PENDING, Appointment.Status.CONFIRMED);

    private final AppointmentRepository appointmentRepository;

    public DuplicateAppointmentValidator(AppointmentRepository appointmentRepository) {
        this.appointmentRepository = appointmentRepository;
    }

    public void validate(String patientEmail, String patientPhone, Long providerId, Instant startDateTime,
            Instant endDateTime) {
        boolean overlapsExistingActive = appointmentRepository.existsActiveOverlapping(
                patientEmail, patientPhone, providerId, ACTIVE_STATUSES, startDateTime, endDateTime);
        if (overlapsExistingActive) {
            throw new DuplicateAppointmentException();
        }
    }
}
