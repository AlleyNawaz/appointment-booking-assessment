package com.clinic.booking.staff.service;

import com.clinic.booking.audit.AuditLogWriter;
import com.clinic.booking.booking.domain.Appointment;
import com.clinic.booking.staff.domain.StaffUser;
import com.clinic.booking.staff.dto.AuditLogPageResponse;
import com.clinic.booking.staff.repository.StaffUserRepository;
import com.clinic.booking.staff.security.StaffUserPrincipal;
import org.junit.jupiter.api.AfterEach;
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
 * Integration tests for {@link AuditLogService} (PRD §8.18) against the real
 * local MySQL schema — same pattern established in Milestones 9-10. Covers
 * the Milestone 11 validation checklist: only ROLE_SYSADMIN may call
 * GET /audit-log; every other role gets 403.
 */
@SpringBootTest
class AuditLogControllerIT {

    private static final String KNOWN_HASH = "$2a$12$iZOajtQvoYA9vG2I/cG0POWBVtiyaczx3rSq7P1M5OlX5njcVqPVq";

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private AuditLogWriter auditLogWriter;

    @Autowired
    private StaffUserRepository staffUserRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<String> staffUsernames = new java.util.ArrayList<>();
    private long providerId;
    private long appointmentId;

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        for (String username : staffUsernames) {
            jdbcTemplate.update("DELETE FROM staff_users WHERE username = ?", username);
        }
        staffUsernames.clear();
        if (appointmentId != 0) {
            jdbcTemplate.update("DELETE FROM appointment_audit_log WHERE appointment_id = ?", appointmentId);
            jdbcTemplate.update("DELETE FROM appointments WHERE id = ?", appointmentId);
        }
        if (providerId != 0) {
            jdbcTemplate.update("DELETE FROM providers WHERE id = ?", providerId);
        }
    }

    @Test
    void sysAdminCanReadTheAuditLog() {
        seedAppointment();
        auditLogWriter.write(appointmentId, Appointment.Status.PENDING, Appointment.Status.EXPIRED, "SYSTEM", null);
        actAs(StaffUser.Role.ROLE_SYSADMIN);

        AuditLogPageResponse response = auditLogService.list(null, null, null, 0, 20, "changedAt,asc");

        assertThat(response.content()).isNotEmpty();
        assertThat(response.size()).isEqualTo(20);
    }

    @Test
    void everyOtherRoleIsDenied() {
        for (StaffUser.Role role : List.of(
                StaffUser.Role.ROLE_STAFF, StaffUser.Role.ROLE_PROVIDER, StaffUser.Role.ROLE_ADMIN)) {
            actAs(role);
            assertThatThrownBy(() -> auditLogService.list(null, null, null, 0, 20, "changedAt,asc"))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    private void seedAppointment() {
        String email = "audit-log-it-" + UUID.randomUUID() + "@example.com";
        jdbcTemplate.update(
                "INSERT INTO providers (first_name, last_name, specialty, email, timezone, is_active) "
                        + "VALUES ('Test', 'Provider', 'General Medicine', ?, 'America/New_York', TRUE)",
                email);
        providerId = jdbcTemplate.queryForObject("SELECT id FROM providers WHERE email = ?", Long.class, email);

        String token = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO appointments (confirmation_token, provider_id, appointment_type_id, "
                        + "patient_full_name, patient_email, patient_phone, start_datetime, end_datetime, "
                        + "status, idempotency_key, request_body_hash) "
                        + "VALUES (?, ?, 2, 'Jordan Rivera', 'jordan@example.com', '+14155551234', "
                        + "'2026-08-20 13:00:00', '2026-08-20 13:30:00', 'EXPIRED', ?, "
                        + "'0000000000000000000000000000000000000000000000000000000000000000')",
                token, providerId, UUID.randomUUID().toString());
        appointmentId = jdbcTemplate.queryForObject(
                "SELECT id FROM appointments WHERE confirmation_token = ?", Long.class, token);
    }

    private void actAs(StaffUser.Role role) {
        String username = "audit-log-it-" + role + "-" + UUID.randomUUID();
        staffUsernames.add(username);
        Long roleProviderId = null;
        if (role == StaffUser.Role.ROLE_PROVIDER) {
            if (providerId == 0) {
                seedAppointment();
            }
            roleProviderId = providerId;
        }
        jdbcTemplate.update(
                "INSERT INTO staff_users (username, password_hash, role, provider_id, is_active) VALUES (?, ?, ?, ?, TRUE)",
                username, KNOWN_HASH, role.name(), roleProviderId);
        StaffUser staffUser = staffUserRepository.findByUsername(username).orElseThrow();
        StaffUserPrincipal principal = new StaffUserPrincipal(staffUser);
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
