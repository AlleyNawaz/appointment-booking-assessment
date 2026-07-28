package com.clinic.booking.staff.dto;

import com.clinic.booking.booking.domain.Appointment;

import java.time.Instant;

/**
 * Staff-facing appointment shape (PRD §8.9/§8.10) — unlike the patient-facing
 * {@code AppointmentDetailResponse}, this exposes the internal {@code id}
 * (path variable for §8.10's transition endpoints) and {@code version} (the
 * {@code If-Match} value for optimistic locking), plus full patient contact
 * details staff need to act on the booking.
 */
public record StaffAppointmentResponse(
        Long id,
        String confirmationToken,
        Long providerId,
        Long appointmentTypeId,
        String patientFullName,
        String patientEmail,
        String patientPhone,
        String notes,
        Instant startDateTime,
        Instant endDateTime,
        Appointment.Status status,
        int version,
        Instant createdAt) {

    public static StaffAppointmentResponse from(Appointment appointment) {
        return new StaffAppointmentResponse(
                appointment.getId(),
                appointment.getConfirmationToken(),
                appointment.getProviderId(),
                appointment.getAppointmentTypeId(),
                appointment.getPatientFullName(),
                appointment.getPatientEmail(),
                appointment.getPatientPhone(),
                appointment.getNotes(),
                appointment.getStartDatetime(),
                appointment.getEndDatetime(),
                appointment.getStatus(),
                appointment.getVersion(),
                appointment.getCreatedAt());
    }
}
