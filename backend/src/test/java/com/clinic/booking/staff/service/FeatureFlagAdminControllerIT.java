package com.clinic.booking.staff.service;

import com.clinic.booking.config.FeatureFlagService;
import com.clinic.booking.staff.domain.StaffUser;
import com.clinic.booking.staff.dto.FeatureFlagRequest;
import com.clinic.booking.staff.dto.FeatureFlagResponse;
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
 * Integration tests for {@link FeatureFlagAdminService} (PRD §8.17) against
 * the real local MySQL schema — same pattern established in Milestones 1-9.
 * Covers the Milestone 10 validation checklist: PUT succeeds for both
 * ROLE_ADMIN and ROLE_SYSADMIN (the one deliberate exception), and the
 * writing instance's cache is evicted synchronously.
 */
@SpringBootTest
class FeatureFlagAdminControllerIT {

    private static final String KNOWN_HASH = "$2a$12$iZOajtQvoYA9vG2I/cG0POWBVtiyaczx3rSq7P1M5OlX5njcVqPVq";
    private static final String FLAG_SQL = "UPDATE feature_flags SET is_enabled = ? WHERE flag_name = 'enable_online_booking'";

    @Autowired
    private FeatureFlagAdminService featureFlagAdminService;

    @Autowired
    private FeatureFlagService featureFlagService;

    @Autowired
    private StaffUserRepository staffUserRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<String> staffUsernames = new java.util.ArrayList<>();

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        for (String username : staffUsernames) {
            jdbcTemplate.update("DELETE FROM staff_users WHERE username = ?", username);
        }
        staffUsernames.clear();
        jdbcTemplate.update(FLAG_SQL, false);
        featureFlagService.evict(FeatureFlagService.ENABLE_ONLINE_BOOKING);
    }

    @Test
    void adminToggle_succeedsAndEvictsCacheImmediately() {
        actAs(StaffUser.Role.ROLE_ADMIN);
        featureFlagService.isEnabled(FeatureFlagService.ENABLE_ONLINE_BOOKING); // warm the cache with the old value

        FeatureFlagResponse response = featureFlagAdminService.update(
                FeatureFlagService.ENABLE_ONLINE_BOOKING, new FeatureFlagRequest(true), currentPrincipal());

        assertThat(response.isEnabled()).isTrue();
        assertThat(response.updatedBy()).isEqualTo(currentPrincipal().getUsername());
        assertThat(featureFlagService.isEnabled(FeatureFlagService.ENABLE_ONLINE_BOOKING)).isTrue();
    }

    @Test
    void sysAdminToggle_alsoSucceeds_theOneNamedException() {
        actAs(StaffUser.Role.ROLE_SYSADMIN);

        FeatureFlagResponse response = featureFlagAdminService.update(
                FeatureFlagService.ENABLE_ONLINE_BOOKING, new FeatureFlagRequest(true), currentPrincipal());

        assertThat(response.isEnabled()).isTrue();
    }

    @Test
    void staffCannotReadOrWriteTheFlag() {
        actAs(StaffUser.Role.ROLE_STAFF);

        assertThatThrownBy(() -> featureFlagAdminService.get(FeatureFlagService.ENABLE_ONLINE_BOOKING))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> featureFlagAdminService.update(
                FeatureFlagService.ENABLE_ONLINE_BOOKING, new FeatureFlagRequest(true), currentPrincipal()))
                .isInstanceOf(AccessDeniedException.class);
    }

    private void actAs(StaffUser.Role role) {
        String username = "flag-admin-it-" + role + "-" + UUID.randomUUID();
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

    private StaffUserPrincipal currentPrincipal() {
        return (StaffUserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
