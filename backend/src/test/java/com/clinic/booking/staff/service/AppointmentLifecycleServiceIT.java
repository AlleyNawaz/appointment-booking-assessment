package com.clinic.booking.staff.service;

import com.clinic.booking.audit.AppointmentAuditLog;
import com.clinic.booking.audit.AppointmentAuditLogRepository;
import com.clinic.booking.booking.domain.Appointment;
import com.clinic.booking.common.exception.StaleVersionException;
import com.clinic.booking.staff.domain.StaffUser;
import com.clinic.booking.staff.dto.AppointmentPageResponse;
import com.clinic.booking.staff.dto.StaffAppointmentResponse;
import com.clinic.booking.staff.repository.StaffUserRepository;
import com.clinic.booking.staff.security.StaffUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link AppointmentLifecycleService} (PRD §8.9/§8.10)
 * against the real local MySQL schema — same pattern established in
 * Milestones 1-8 (no Testcontainers). Covers the Milestone 9 validation
 * checklist: pagination clamping (§19 #42/#43), concurrent-transition
 * STALE_VERSION (§19 #15), provider scoping (§19 #49), the
 * round-3 role-hierarchy fix (ROLE_SYSADMIN excluded from mutations),
 * invalid-transition rejection (§19 #24), and one audit row per transition.
 */
@SpringBootTest
class AppointmentLifecycleServiceIT {

    private static final String KNOWN_HASH = "$2a$12$iZOajtQvoYA9vG2I/cG0POWBVtiyaczx3rSq7P1M5OlX5njcVqPVq";
    private static final java.util.Calendar UTC_CALENDAR =
            java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));

    @Autowired
    private AppointmentLifecycleService appointmentLifecycleService;

    @Autowired
    private StaffUserRepository staffUserRepository;

    @Autowired
    private AppointmentAuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long providerId;
    private long otherProviderId;
    private final List<String> staffUsernames = new java.util.ArrayList<>();
    private final java.util.concurrent.atomic.AtomicInteger appointmentSequence =
            new java.util.concurrent.atomic.AtomicInteger();

    @BeforeEach
    void seedProviders() {
        providerId = seedProvider();
        otherProviderId = seedProvider();
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        for (String username : staffUsernames) {
            jdbcTemplate.update("DELETE FROM staff_users WHERE username = ?", username);
        }
        staffUsernames.clear();
        jdbcTemplate.update("DELETE FROM appointment_audit_log WHERE appointment_id IN "
                + "(SELECT id FROM appointments WHERE provider_id IN (?, ?))", providerId, otherProviderId);
        jdbcTemplate.update("DELETE FROM appointments WHERE provider_id IN (?, ?)", providerId, otherProviderId);
        jdbcTemplate.update("DELETE FROM providers WHERE id IN (?, ?)", providerId, otherProviderId);
    }

    @Test
    void pageBeyondData_returnsEmptyContentNotNotFound_andSizeIsClamped() {
        long appointmentId = seedAppointment(providerId, Appointment.Status.CONFIRMED);
        actAs(StaffUser.Role.ROLE_STAFF, null);

        AppointmentPageResponse farPage = appointmentLifecycleService.list(
                null, providerId, null, null, 999, 20, "startDateTime,asc", currentPrincipal());
        assertThat(farPage.content()).isEmpty();
        assertThat(farPage.totalElements()).isGreaterThanOrEqualTo(1);

        AppointmentPageResponse clamped = appointmentLifecycleService.list(
                null, providerId, null, null, 0, 10000, "startDateTime,asc", currentPrincipal());
        assertThat(clamped.size()).isEqualTo(100);

        assertThat(appointmentId).isPositive();
    }

    @Test
    void secondConcurrentTransition_getsStaleVersion() {
        long appointmentId = seedAppointment(providerId, Appointment.Status.PENDING);
        actAs(StaffUser.Role.ROLE_STAFF, null);

        StaffAppointmentResponse approved = appointmentLifecycleService.approve(appointmentId, 0, currentPrincipal());
        assertThat(approved.status()).isEqualTo(Appointment.Status.CONFIRMED);

        // A second staff member who read the appointment before the first approval still
        // holds version 0 — their attempt now fails with the same conflict, per §19 #15.
        assertThatThrownBy(() -> appointmentLifecycleService.approve(appointmentId, 0, currentPrincipal()))
                .isInstanceOf(StaleVersionException.class);
    }

    @Test
    void providerCannotActOnAnotherProvidersAppointment() {
        long appointmentId = seedAppointment(otherProviderId, Appointment.Status.PENDING);
        actAs(StaffUser.Role.ROLE_PROVIDER, providerId);

        assertThatThrownBy(() -> appointmentLifecycleService.approve(appointmentId, 0, currentPrincipal()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void sysAdminCannotApproveRejectOrComplete() {
        long pendingId = seedAppointment(providerId, Appointment.Status.PENDING);
        long confirmedId = seedAppointment(providerId, Appointment.Status.CONFIRMED);
        actAs(StaffUser.Role.ROLE_SYSADMIN, null);

        assertThatThrownBy(() -> appointmentLifecycleService.approve(pendingId, 0, currentPrincipal()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> appointmentLifecycleService.reject(pendingId, "no", 0, currentPrincipal()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> appointmentLifecycleService.complete(confirmedId, 0, currentPrincipal()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void invalidTransition_cancelledToCompleted_isRejectedAsConflict() {
        long appointmentId = seedAppointment(providerId, Appointment.Status.CANCELLED);
        actAs(StaffUser.Role.ROLE_STAFF, null);

        assertThatThrownBy(() -> appointmentLifecycleService.complete(appointmentId, 0, currentPrincipal()))
                .isInstanceOf(StaleVersionException.class);
    }

    @Test
    void everyTransitionWritesExactlyOneAuditRowWithTheStaffUsername() {
        long appointmentId = seedAppointment(providerId, Appointment.Status.PENDING);
        String username = actAs(StaffUser.Role.ROLE_STAFF, null);

        appointmentLifecycleService.approve(appointmentId, 0, currentPrincipal());

        List<AppointmentAuditLog> rows = auditLogRepository.findAll().stream()
                .filter(row -> row.getAppointmentId().equals(appointmentId))
                .toList();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getChangedBy()).isEqualTo(username);
        assertThat(rows.get(0).getPreviousStatus()).isEqualTo("PENDING");
        assertThat(rows.get(0).getNewStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    void missedAppointment_canBeCorrectedToCompleted_withinSevenDays() {
        long appointmentId = seedAppointmentWithEndDatetime(
                providerId, Appointment.Status.MISSED, java.time.Instant.now().minus(3, java.time.temporal.ChronoUnit.DAYS));
        actAs(StaffUser.Role.ROLE_STAFF, null);

        StaffAppointmentResponse response = appointmentLifecycleService.complete(appointmentId, 0, currentPrincipal());

        assertThat(response.status()).isEqualTo(Appointment.Status.COMPLETED);
    }

    @Test
    void missedAppointment_beyondSevenDays_cannotBeCorrected() {
        long appointmentId = seedAppointmentWithEndDatetime(
                providerId, Appointment.Status.MISSED, java.time.Instant.now().minus(10, java.time.temporal.ChronoUnit.DAYS));
        actAs(StaffUser.Role.ROLE_STAFF, null);

        assertThatThrownBy(() -> appointmentLifecycleService.complete(appointmentId, 0, currentPrincipal()))
                .isInstanceOf(StaleVersionException.class);
    }

    private long seedAppointmentWithEndDatetime(long providerId, Appointment.Status status, java.time.Instant endDatetime) {
        String token = UUID.randomUUID().toString();
        String idempotencyKey = UUID.randomUUID().toString();
        java.time.Instant start = endDatetime.minusSeconds(1800);
        jdbcTemplate.update(con -> {
            java.sql.PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO appointments (confirmation_token, provider_id, appointment_type_id, "
                            + "patient_full_name, patient_email, patient_phone, start_datetime, end_datetime, "
                            + "status, idempotency_key, request_body_hash) "
                            + "VALUES (?, ?, 2, 'Jordan Rivera', 'jordan@example.com', '+14155551234', ?, ?, "
                            + "?, ?, '0000000000000000000000000000000000000000000000000000000000000000')");
            ps.setString(1, token);
            ps.setLong(2, providerId);
            ps.setTimestamp(3, java.sql.Timestamp.from(start), UTC_CALENDAR);
            ps.setTimestamp(4, java.sql.Timestamp.from(endDatetime), UTC_CALENDAR);
            ps.setString(5, status.name());
            ps.setString(6, idempotencyKey);
            return ps;
        });
        return jdbcTemplate.queryForObject("SELECT id FROM appointments WHERE confirmation_token = ?", Long.class, token);
    }

    private long seedProvider() {
        String email = "lifecycle-it-" + UUID.randomUUID() + "@example.com";
        jdbcTemplate.update(
                "INSERT INTO providers (first_name, last_name, specialty, email, timezone, is_active) "
                        + "VALUES ('Test', 'Provider', 'General Medicine', ?, 'America/New_York', TRUE)",
                email);
        return jdbcTemplate.queryForObject("SELECT id FROM providers WHERE email = ?", Long.class, email);
    }

    private long seedAppointment(long providerId, Appointment.Status status) {
        String token = UUID.randomUUID().toString();
        String idempotencyKey = UUID.randomUUID().toString();
        int minuteOffset = appointmentSequence.getAndIncrement();
        jdbcTemplate.update(
                "INSERT INTO appointments (confirmation_token, provider_id, appointment_type_id, patient_full_name, "
                        + "patient_email, patient_phone, start_datetime, end_datetime, status, idempotency_key, "
                        + "request_body_hash) VALUES (?, ?, 2, 'Jordan Rivera', 'jordan@example.com', "
                        + "'+14155551234', DATE_ADD('2026-08-20 13:00:00', INTERVAL ? MINUTE), "
                        + "DATE_ADD('2026-08-20 13:30:00', INTERVAL ? MINUTE), ?, ?, "
                        + "'0000000000000000000000000000000000000000000000000000000000000000')",
                token, providerId, minuteOffset, minuteOffset, status.name(), idempotencyKey);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM appointments WHERE confirmation_token = ?", Long.class, token);
    }

    private String actAs(StaffUser.Role role, Long providerId) {
        String username = "lifecycle-it-" + role + "-" + UUID.randomUUID();
        staffUsernames.add(username);
        jdbcTemplate.update(
                "INSERT INTO staff_users (username, password_hash, role, provider_id, is_active) VALUES (?, ?, ?, ?, TRUE)",
                username, KNOWN_HASH, role.name(), providerId);
        StaffUser staffUser = staffUserRepository.findByUsername(username).orElseThrow();
        StaffUserPrincipal principal = new StaffUserPrincipal(staffUser);
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return username;
    }

    private StaffUserPrincipal currentPrincipal() {
        return (StaffUserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
