package com.clinic.booking.staff.service;

import com.clinic.booking.staff.domain.StaffUser;
import com.clinic.booking.staff.dto.UnavailabilityRequest;
import com.clinic.booking.staff.dto.UnavailabilityResponse;
import com.clinic.booking.staff.repository.StaffUserRepository;
import com.clinic.booking.staff.security.StaffUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link UnavailabilityService} (PRD §8.15/§7.5) against
 * the real local MySQL schema. Covers the Milestone 10 validation checklist:
 * creating {@code provider_unavailability} overlapping an existing
 * {@code CONFIRMED} appointment returns it in {@code affectedAppointments}
 * (§19 #20/§12.3).
 */
@SpringBootTest
class UnavailabilityServiceIT {

    private static final String KNOWN_HASH = "$2a$12$iZOajtQvoYA9vG2I/cG0POWBVtiyaczx3rSq7P1M5OlX5njcVqPVq";

    @Autowired
    private UnavailabilityService unavailabilityService;

    @Autowired
    private StaffUserRepository staffUserRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long providerId;
    private String appointmentToken;
    private String staffUsername;

    @BeforeEach
    void seedProviderAndAppointment() {
        String email = "unavail-it-" + UUID.randomUUID() + "@example.com";
        jdbcTemplate.update(
                "INSERT INTO providers (first_name, last_name, specialty, email, timezone, is_active) "
                        + "VALUES ('Test', 'Provider', 'General Medicine', ?, 'America/New_York', TRUE)",
                email);
        providerId = jdbcTemplate.queryForObject("SELECT id FROM providers WHERE email = ?", Long.class, email);

        appointmentToken = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO appointments (confirmation_token, provider_id, appointment_type_id, patient_full_name, "
                        + "patient_email, patient_phone, start_datetime, end_datetime, status, idempotency_key, "
                        + "request_body_hash) VALUES (?, ?, 2, 'Jordan Rivera', 'jordan@example.com', "
                        + "'+14155551234', '2026-08-16 14:00:00', '2026-08-16 14:30:00', 'CONFIRMED', ?, "
                        + "'0000000000000000000000000000000000000000000000000000000000000000')",
                appointmentToken, providerId, UUID.randomUUID().toString());
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        jdbcTemplate.update("DELETE FROM provider_unavailability WHERE provider_id = ?", providerId);
        jdbcTemplate.update("DELETE FROM appointments WHERE confirmation_token = ?", appointmentToken);
        jdbcTemplate.update("DELETE FROM staff_users WHERE username = ?", staffUsername);
        jdbcTemplate.update("DELETE FROM providers WHERE id = ?", providerId);
    }

    @Test
    void creatingUnavailability_returnsOverlappingConfirmedAppointmentInAffectedAppointments() {
        actAsStaff();

        UnavailabilityResponse response = unavailabilityService.create(providerId,
                new UnavailabilityRequest(
                        Instant.parse("2026-08-15T00:00:00Z"), Instant.parse("2026-08-22T00:00:00Z"), "Vacation"),
                currentPrincipal());

        assertThat(response.affectedAppointments()).hasSize(1);
        assertThat(response.affectedAppointments().get(0).confirmationToken()).isEqualTo(appointmentToken);
    }

    private void actAsStaff() {
        staffUsername = "unavail-it-" + UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO staff_users (username, password_hash, role, is_active) VALUES (?, ?, 'ROLE_STAFF', TRUE)",
                staffUsername, KNOWN_HASH);
        StaffUser staffUser = staffUserRepository.findByUsername(staffUsername).orElseThrow();
        StaffUserPrincipal principal = new StaffUserPrincipal(staffUser);
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private StaffUserPrincipal currentPrincipal() {
        return (StaffUserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
