package com.clinic.booking.booking.dto;

/**
 * Request shape for POST /booking/appointments (PRD §8.6):
 * {@code { "holdToken", "patientFullName", "patientEmail", "patientPhone", "notes" } }.
 * No {@code appointmentTypeId} field — it is resolved server-side from the
 * {@code slot_holds} row for {@code holdToken} (§7.8/§8.6).
 *
 * <p>Deliberately has no {@code @NotNull}/{@code @Valid} bean-validation
 * annotations, for the same ordering-safety reason as {@code HoldRequest}
 * (Milestone 4): those would be enforced during Spring MVC argument
 * resolution, ahead of {@code @FeatureGate}'s AOP advice.
 */
public record CreateAppointmentRequest(
        String holdToken, String patientFullName, String patientEmail, String patientPhone, String notes) {
}
