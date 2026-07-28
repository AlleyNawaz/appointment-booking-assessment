package com.clinic.booking.staff.service;

import com.clinic.booking.booking.domain.ProviderAvailabilityRule;
import com.clinic.booking.booking.repository.ProviderAvailabilityRuleRepository;
import com.clinic.booking.common.exception.AvailabilityRuleOverlapException;
import com.clinic.booking.staff.domain.StaffUser;
import com.clinic.booking.staff.dto.AvailabilityRuleRequest;
import com.clinic.booking.staff.dto.AvailabilityRuleResponse;
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

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link AvailabilityRuleService} (PRD §8.14) against
 * the real local MySQL schema — same pattern established in Milestones 1-9.
 * Covers the Milestone 10 validation checklist: overlap rejection before
 * persistence (§19 #38) and the round-3 role-hierarchy fix (ROLE_SYSADMIN
 * excluded from writes, included in reads).
 */
@SpringBootTest
class AvailabilityRuleServiceIT {

    private static final String KNOWN_HASH = "$2a$12$iZOajtQvoYA9vG2I/cG0POWBVtiyaczx3rSq7P1M5OlX5njcVqPVq";

    @Autowired
    private AvailabilityRuleService availabilityRuleService;

    @Autowired
    private ProviderAvailabilityRuleRepository ruleRepository;

    @Autowired
    private StaffUserRepository staffUserRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long providerId;
    private final List<String> staffUsernames = new java.util.ArrayList<>();

    @BeforeEach
    void seedProvider() {
        String email = "avail-rule-it-" + UUID.randomUUID() + "@example.com";
        jdbcTemplate.update(
                "INSERT INTO providers (first_name, last_name, specialty, email, timezone, is_active) "
                        + "VALUES ('Test', 'Provider', 'General Medicine', ?, 'America/New_York', TRUE)",
                email);
        providerId = jdbcTemplate.queryForObject("SELECT id FROM providers WHERE email = ?", Long.class, email);
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        for (String username : staffUsernames) {
            jdbcTemplate.update("DELETE FROM staff_users WHERE username = ?", username);
        }
        staffUsernames.clear();
        jdbcTemplate.update("DELETE FROM provider_availability_rules WHERE provider_id = ?", providerId);
        jdbcTemplate.update("DELETE FROM providers WHERE id = ?", providerId);
    }

    @Test
    void overlappingRule_rejectedBeforePersistence() {
        actAs(StaffUser.Role.ROLE_ADMIN, null);
        availabilityRuleService.create(providerId,
                new AvailabilityRuleRequest(1, LocalTime.of(9, 0), LocalTime.of(13, 0),
                        ProviderAvailabilityRule.RuleType.WORKING));

        assertThatThrownBy(() -> availabilityRuleService.create(providerId,
                new AvailabilityRuleRequest(1, LocalTime.of(12, 0), LocalTime.of(14, 0),
                        ProviderAvailabilityRule.RuleType.BREAK)))
                .isInstanceOf(AvailabilityRuleOverlapException.class);

        assertThat(ruleRepository.findByProviderIdAndDayOfWeek(providerId, 1)).hasSize(1);
    }

    @Test
    void nonOverlappingRule_isPersisted() {
        actAs(StaffUser.Role.ROLE_ADMIN, null);
        availabilityRuleService.create(providerId,
                new AvailabilityRuleRequest(2, LocalTime.of(9, 0), LocalTime.of(12, 0),
                        ProviderAvailabilityRule.RuleType.WORKING));

        AvailabilityRuleResponse response = availabilityRuleService.create(providerId,
                new AvailabilityRuleRequest(2, LocalTime.of(12, 0), LocalTime.of(13, 0),
                        ProviderAvailabilityRule.RuleType.BREAK));

        assertThat(response.id()).isNotNull();
        assertThat(ruleRepository.findByProviderIdAndDayOfWeek(providerId, 2)).hasSize(2);
    }

    @Test
    void sysAdminCannotCreateRules_butCanListThem() {
        actAs(StaffUser.Role.ROLE_ADMIN, null);
        availabilityRuleService.create(providerId,
                new AvailabilityRuleRequest(3, LocalTime.of(9, 0), LocalTime.of(12, 0),
                        ProviderAvailabilityRule.RuleType.WORKING));

        actAs(StaffUser.Role.ROLE_SYSADMIN, null);
        assertThatThrownBy(() -> availabilityRuleService.create(providerId,
                new AvailabilityRuleRequest(4, LocalTime.of(9, 0), LocalTime.of(12, 0),
                        ProviderAvailabilityRule.RuleType.WORKING)))
                .isInstanceOf(AccessDeniedException.class);

        List<AvailabilityRuleResponse> rules =
                availabilityRuleService.list(providerId, currentPrincipal());
        assertThat(rules).hasSize(1);
    }

    private void actAs(StaffUser.Role role, Long providerIdForRole) {
        String username = "avail-rule-it-" + role + "-" + UUID.randomUUID();
        staffUsernames.add(username);
        jdbcTemplate.update(
                "INSERT INTO staff_users (username, password_hash, role, provider_id, is_active) VALUES (?, ?, ?, ?, TRUE)",
                username, KNOWN_HASH, role.name(), providerIdForRole);
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
