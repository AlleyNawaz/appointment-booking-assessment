package com.clinic.booking.booking.controller;

import com.clinic.booking.booking.dto.RescheduleRequest;
import com.clinic.booking.booking.dto.RescheduleResponse;
import com.clinic.booking.booking.service.RescheduleService;
import com.clinic.booking.common.exception.MissingRequiredFieldException;
import com.clinic.booking.config.FeatureFlagService;
import com.clinic.booking.config.FeatureGate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/v1/booking/appointments/{confirmationToken}/reschedule (PRD §8.19)
 * — the sixth gated endpoint (§6): a reschedule creates a new
 * {@code active_slot_key} commitment exactly like a fresh booking, so it is
 * gated for the same reason as {@code POST /booking/appointments}, unlike the
 * never-gated GET/DELETE on this same resource ({@link AppointmentLookupController}).
 *
 * <p>Same ordering-safe pattern as {@code AppointmentController} (Milestone 5):
 * the {@code Idempotency-Key} header and the request body are both declared
 * optional so neither throws during Spring MVC argument resolution — which
 * runs before {@code @FeatureGate}'s AOP advice — and presence is checked
 * explicitly in the method body instead, after the flag check has already run.
 *
 * <p>§15.3: uses the same {@code {*confirmationToken}} capture-all pattern as
 * {@link AppointmentLookupController} so a token containing "/" still reaches
 * this controller instead of falling through to Spring's default error handling
 * — the captured value includes its own leading "/" and the fixed
 * "/reschedule" suffix, both stripped before the normal confirmation-token
 * lookup runs, so a malformed token and an unknown-but-well-formed token are
 * indistinguishable: both simply fail to match a stored row and surface the
 * same {@code APPOINTMENT_NOT_FOUND}.
 */
@RestController
@RequestMapping("/api/v1/booking/appointments")
public class RescheduleController {

    private static final String RESCHEDULE_SUFFIX = "/reschedule";

    private final RescheduleService rescheduleService;

    public RescheduleController(RescheduleService rescheduleService) {
        this.rescheduleService = rescheduleService;
    }

    @FeatureGate(FeatureFlagService.ENABLE_ONLINE_BOOKING)
    @PostMapping("/{*confirmationToken}")
    @ResponseStatus(HttpStatus.CREATED)
    public RescheduleResponse reschedule(
            @PathVariable String confirmationToken,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) RescheduleRequest request) {
        if (idempotencyKey == null) {
            throw new MissingRequiredFieldException("Idempotency-Key");
        }
        if (request == null) {
            throw new MissingRequiredFieldException("request body");
        }
        if (request.holdToken() == null) {
            throw new MissingRequiredFieldException("holdToken");
        }
        return rescheduleService.reschedule(
                extractConfirmationToken(confirmationToken), request.holdToken(), idempotencyKey, request.reason());
    }

    /**
     * Strips the capture-all's leading "/" and the fixed "/reschedule" suffix. A captured
     * value that doesn't end with that suffix is passed through unchanged rather than special-cased
     * — it simply won't match any stored {@code confirmation_token} either, reaching the exact
     * same {@code AppointmentNotFoundException} as any other unknown token (§15.3).
     */
    private static String extractConfirmationToken(String captured) {
        String withoutLeadingSlash = captured.startsWith("/") ? captured.substring(1) : captured;
        return withoutLeadingSlash.endsWith(RESCHEDULE_SUFFIX)
                ? withoutLeadingSlash.substring(0, withoutLeadingSlash.length() - RESCHEDULE_SUFFIX.length())
                : withoutLeadingSlash;
    }
}
