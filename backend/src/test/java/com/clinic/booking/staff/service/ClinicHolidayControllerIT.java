package com.clinic.booking.staff.service;

import com.clinic.booking.staff.domain.StaffUser;
import com.clinic.booking.staff.dto.HolidayRequest;
import com.clinic.booking.staff.dto.HolidayResponse;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link ClinicHolidayService} (PRD §8.16) against the
 * real local MySQL schema — same pattern established in Milestones 1-9.
 * Covers the Milestone 10 validation checklist: ROLE_ADMIN can CRUD
 * holidays; ROLE_SYSADMIN gets 403 on writes but 200 (success) on reads.
 */
@SpringBootTest
class ClinicHolidayControllerIT {

    private static final String KNOWN_HASH = "$2a$12$iZOajtQvoYA9vG2I/cG0POWBVtiyaczx3rSq7P1M5OlX5njcVqPVq";

    @Autowired
    private ClinicHolidayService clinicHolidayService;

    @Autowired
    private StaffUserRepository staffUserRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<String> staffUsernames = new java.util.ArrayList<>();
    private final List<Long> createdHolidayIds = new java.util.ArrayList<>();

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        for (String username : staffUsernames) {
            jdbcTemplate.update("DELETE FROM staff_users WHERE username = ?", username);
        }
        staffUsernames.clear();
        for (Long id : createdHolidayIds) {
            jdbcTemplate.update("DELETE FROM clinic_holidays WHERE id = ?", id);
        }
        createdHolidayIds.clear();
    }

    @Test
    void adminCanCreateUpdateAndDeleteHolidays() {
        actAs(StaffUser.Role.ROLE_ADMIN);
        LocalDate date = uniqueDate();
        HolidayResponse created = clinicHolidayService.create(new HolidayRequest(date, "Test Holiday", false));
        createdHolidayIds.add(created.id());

        LocalDate updatedDate = date.plusDays(1);
        HolidayResponse updated = clinicHolidayService.update(created.id(),
                new HolidayRequest(updatedDate, "Test Holiday Renamed", true));
        assertThat(updated.name()).isEqualTo("Test Holiday Renamed");
        assertThat(updated.holidayDate()).isEqualTo(updatedDate);
        assertThat(updated.isRecurringAnnually()).isTrue();

        clinicHolidayService.delete(created.id());
        createdHolidayIds.remove(created.id());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM clinic_holidays WHERE id = ?", Integer.class, created.id()))
                .isZero();
    }

    @Test
    void sysAdminCannotWrite_butCanRead() {
        actAs(StaffUser.Role.ROLE_ADMIN);
        LocalDate date = uniqueDate();
        HolidayResponse created = clinicHolidayService.create(new HolidayRequest(date, "Test Holiday", false));
        createdHolidayIds.add(created.id());

        actAs(StaffUser.Role.ROLE_SYSADMIN);
        assertThatThrownBy(() -> clinicHolidayService.create(new HolidayRequest(uniqueDate(), "Sysadmin Holiday", false)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> clinicHolidayService.update(created.id(),
                new HolidayRequest(date, "Changed", false)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> clinicHolidayService.delete(created.id()))
                .isInstanceOf(AccessDeniedException.class);

        List<HolidayResponse> holidays = clinicHolidayService.list();
        assertThat(holidays).anyMatch(h -> h.id().equals(created.id()));
    }

    private static LocalDate uniqueDate() {
        return LocalDate.of(2030, 1, 1).plusDays(System.nanoTime() % 3000);
    }

    private void actAs(StaffUser.Role role) {
        String username = "holiday-it-" + role + "-" + UUID.randomUUID();
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
