package com.clinic.booking.booking.dto;

/**
 * Request shape for POST /booking/appointments/{confirmationToken}/reschedule
 * (PRD §8.19): {@code { "holdToken", "reason" } } — {@code reason} optional,
 * mirrors {@code cancellation_reason}. The new slot's provider/type/time come
 * from the {@code slot_holds} row for {@code holdToken}, not raw fields here.
 *
 * <p>Deliberately has no {@code @NotNull}/{@code @Valid} bean-validation
 * annotations, for the same ordering-safety reason as {@code CreateAppointmentRequest}:
 * those would be enforced during Spring MVC argument resolution, ahead of
 * {@code @FeatureGate}'s AOP advice.
 */
public record RescheduleRequest(String holdToken, String reason) {
}
