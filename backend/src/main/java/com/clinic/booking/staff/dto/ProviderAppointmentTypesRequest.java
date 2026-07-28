package com.clinic.booking.staff.dto;

import java.util.List;

/** Request body for PUT /staff/providers/{id}/appointment-types (PRD §8.13). */
public record ProviderAppointmentTypesRequest(List<Long> appointmentTypeIds) {
}
