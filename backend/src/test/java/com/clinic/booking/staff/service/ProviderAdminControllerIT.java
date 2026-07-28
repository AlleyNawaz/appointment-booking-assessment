package com.clinic.booking.staff.service;

import com.clinic.booking.common.exception.InvalidAppointmentTypeReferenceException;
import com.clinic.booking.staff.domain.StaffUser;
import com.clinic.booking.staff.dto.ProviderAdminResponse;
import com.clinic.booking.staff.dto.ProviderRequest;
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
 * Integration tests for {@link ProviderAdminService} (PRD §8.13) against the
 * real local MySQL schema — same pattern established in Milestones 1-9.
 * Covers the Milestone 10 validation checklist: ROLE_ADMIN can CRUD
 * providers; ROLE_SYSADMIN gets 403 on writes but 200 (success) on reads.
 */
@SpringBootTest
class ProviderAdminControllerIT {

    private static final String KNOWN_HASH = "$2a$12$iZOajtQvoYA9vG2I/cG0POWBVtiyaczx3rSq7P1M5OlX5njcVqPVq";
    private static final long NEW_PATIENT_TYPE_ID = 1L;
    private static final long GENERAL_CONSULT_TYPE_ID = 2L;
    private static final long NONEXISTENT_TYPE_ID = 999_999L;

    @Autowired
    private ProviderAdminService providerAdminService;

    @Autowired
    private StaffUserRepository staffUserRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<String> staffUsernames = new java.util.ArrayList<>();
    private final List<Long> createdProviderIds = new java.util.ArrayList<>();

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        for (String username : staffUsernames) {
            jdbcTemplate.update("DELETE FROM staff_users WHERE username = ?", username);
        }
        staffUsernames.clear();
        for (Long id : createdProviderIds) {
            jdbcTemplate.update("DELETE FROM provider_appointment_types WHERE provider_id = ?", id);
            jdbcTemplate.update("DELETE FROM providers WHERE id = ?", id);
        }
        createdProviderIds.clear();
    }

    @Test
    void adminCanCreateUpdateAndDeleteProviders() {
        actAs(StaffUser.Role.ROLE_ADMIN);
        String email = "provider-admin-it-" + UUID.randomUUID() + "@example.com";
        ProviderAdminResponse created = providerAdminService.create(
                new ProviderRequest("Ada", "Okafor", "Family Medicine", email, "America/New_York", true));
        createdProviderIds.add(created.id());

        ProviderAdminResponse updated = providerAdminService.update(created.id(),
                new ProviderRequest("Ada", "Okafor-Smith", "Family Medicine", email, "America/New_York", true));
        assertThat(updated.lastName()).isEqualTo("Okafor-Smith");

        ProviderAdminResponse deleted = providerAdminService.softDelete(created.id());
        assertThat(deleted.isActive()).isFalse();
    }

    @Test
    void sysAdminCannotWrite_butCanRead() {
        actAs(StaffUser.Role.ROLE_ADMIN);
        String email = "provider-admin-it-" + UUID.randomUUID() + "@example.com";
        ProviderAdminResponse created = providerAdminService.create(
                new ProviderRequest("Ada", "Okafor", "Family Medicine", email, "America/New_York", true));
        createdProviderIds.add(created.id());

        actAs(StaffUser.Role.ROLE_SYSADMIN);
        assertThatThrownBy(() -> providerAdminService.create(
                new ProviderRequest("Sam", "Sysadmin", "Family Medicine",
                        "provider-admin-it-" + UUID.randomUUID() + "@example.com", "America/New_York", true)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> providerAdminService.update(created.id(),
                new ProviderRequest("Ada", "Changed", "Family Medicine", email, "America/New_York", true)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> providerAdminService.softDelete(created.id()))
                .isInstanceOf(AccessDeniedException.class);

        List<ProviderAdminResponse> providers = providerAdminService.list();
        assertThat(providers).anyMatch(p -> p.id().equals(created.id()));
    }

    @Test
    void adminCanReplaceProviderAppointmentTypes() {
        actAs(StaffUser.Role.ROLE_ADMIN);
        String email = "provider-admin-it-" + UUID.randomUUID() + "@example.com";
        ProviderAdminResponse created = providerAdminService.create(
                new ProviderRequest("Ada", "Okafor", "Family Medicine", email, "America/New_York", true));
        createdProviderIds.add(created.id());

        ProviderAdminResponse updated = providerAdminService.replaceAppointmentTypes(
                created.id(), List.of(NEW_PATIENT_TYPE_ID, GENERAL_CONSULT_TYPE_ID));

        assertThat(updated.appointmentTypeIds()).containsExactlyInAnyOrder(NEW_PATIENT_TYPE_ID, GENERAL_CONSULT_TYPE_ID);
    }

    @Test
    void replaceProviderAppointmentTypes_unknownId_throwsInvalidAppointmentTypeReference() {
        actAs(StaffUser.Role.ROLE_ADMIN);
        String email = "provider-admin-it-" + UUID.randomUUID() + "@example.com";
        ProviderAdminResponse created = providerAdminService.create(
                new ProviderRequest("Ada", "Okafor", "Family Medicine", email, "America/New_York", true));
        createdProviderIds.add(created.id());

        assertThatThrownBy(() -> providerAdminService.replaceAppointmentTypes(
                created.id(), List.of(NONEXISTENT_TYPE_ID)))
                .isInstanceOf(InvalidAppointmentTypeReferenceException.class);
    }

    @Test
    void sysAdminCannotReplaceProviderAppointmentTypes() {
        actAs(StaffUser.Role.ROLE_ADMIN);
        String email = "provider-admin-it-" + UUID.randomUUID() + "@example.com";
        ProviderAdminResponse created = providerAdminService.create(
                new ProviderRequest("Ada", "Okafor", "Family Medicine", email, "America/New_York", true));
        createdProviderIds.add(created.id());

        actAs(StaffUser.Role.ROLE_SYSADMIN);
        assertThatThrownBy(() -> providerAdminService.replaceAppointmentTypes(
                created.id(), List.of(GENERAL_CONSULT_TYPE_ID)))
                .isInstanceOf(AccessDeniedException.class);
    }

    private void actAs(StaffUser.Role role) {
        String username = "provider-admin-it-" + role + "-" + UUID.randomUUID();
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
