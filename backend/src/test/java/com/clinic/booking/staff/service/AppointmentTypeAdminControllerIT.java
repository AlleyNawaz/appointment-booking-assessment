package com.clinic.booking.staff.service;

import com.clinic.booking.staff.domain.StaffUser;
import com.clinic.booking.staff.dto.AppointmentTypeAdminResponse;
import com.clinic.booking.staff.dto.AppointmentTypeRequest;
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
 * Integration tests for {@link AppointmentTypeAdminService} (PRD §8.12)
 * against the real local MySQL schema — same pattern established in
 * Milestones 1-9. Covers the Milestone 10 validation checklist: ROLE_ADMIN
 * can CRUD appointment types; ROLE_SYSADMIN gets 403 on writes but 200
 * (success) on reads.
 */
@SpringBootTest
class AppointmentTypeAdminControllerIT {

    private static final String KNOWN_HASH = "$2a$12$iZOajtQvoYA9vG2I/cG0POWBVtiyaczx3rSq7P1M5OlX5njcVqPVq";

    @Autowired
    private AppointmentTypeAdminService appointmentTypeAdminService;

    @Autowired
    private StaffUserRepository staffUserRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<String> staffUsernames = new java.util.ArrayList<>();
    private final List<Long> createdTypeIds = new java.util.ArrayList<>();

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        for (String username : staffUsernames) {
            jdbcTemplate.update("DELETE FROM staff_users WHERE username = ?", username);
        }
        staffUsernames.clear();
        for (Long id : createdTypeIds) {
            jdbcTemplate.update("DELETE FROM appointment_types WHERE id = ?", id);
        }
        createdTypeIds.clear();
    }

    @Test
    void adminCanCreateUpdateAndDeactivateAppointmentTypes() {
        actAs(StaffUser.Role.ROLE_ADMIN);
        String code = uniqueCode();
        AppointmentTypeAdminResponse created = appointmentTypeAdminService.create(
                new AppointmentTypeRequest(code, "Test Type", 30, 0, false, true));
        createdTypeIds.add(created.id());

        AppointmentTypeAdminResponse updated = appointmentTypeAdminService.update(created.id(),
                new AppointmentTypeRequest(code, "Test Type Renamed", 45, 5, true, true));
        assertThat(updated.displayName()).isEqualTo("Test Type Renamed");
        assertThat(updated.durationMinutes()).isEqualTo(45);

        AppointmentTypeAdminResponse deactivated = appointmentTypeAdminService.deactivate(created.id());
        assertThat(deactivated.isActive()).isFalse();
    }

    @Test
    void sysAdminCannotWrite_butCanRead() {
        actAs(StaffUser.Role.ROLE_ADMIN);
        String code = uniqueCode();
        AppointmentTypeAdminResponse created = appointmentTypeAdminService.create(
                new AppointmentTypeRequest(code, "Test Type", 30, 0, false, true));
        createdTypeIds.add(created.id());

        actAs(StaffUser.Role.ROLE_SYSADMIN);
        assertThatThrownBy(() -> appointmentTypeAdminService.create(
                new AppointmentTypeRequest(uniqueCode(), "Sysadmin Type", 30, 0, false, true)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> appointmentTypeAdminService.update(created.id(),
                new AppointmentTypeRequest(code, "Changed", 30, 0, false, true)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> appointmentTypeAdminService.deactivate(created.id()))
                .isInstanceOf(AccessDeniedException.class);

        List<AppointmentTypeAdminResponse> types = appointmentTypeAdminService.list();
        assertThat(types).anyMatch(t -> t.id().equals(created.id()));
    }

    private static String uniqueCode() {
        return "TEST_TYPE_" + System.nanoTime();
    }

    private void actAs(StaffUser.Role role) {
        String username = "type-admin-it-" + role + "-" + UUID.randomUUID();
        staffUsernames.add(username);
        jdbcTemplate.update(
                "INSERT INTO staff_users (username, password_hash, role, is_active) VALUES (?, ?, ?, TRUE)",
                username, KNOWN_HASH, role.name());
        StaffUser staffUser = staffUserRepository.findByUsername(username).orElseThrow();
        StaffUserPrincipal principal = new StaffUserPrincipal(staffUser);
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
