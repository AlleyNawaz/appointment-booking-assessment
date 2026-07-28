package com.clinic.booking.booking.job;

import com.clinic.booking.audit.AppointmentAuditLog;
import com.clinic.booking.audit.AppointmentAuditLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link MissedAppointmentJob} (PRD §12.7/§19 #25)
 * against the real local MySQL schema — same pattern established by
 * {@link HoldReaperJob}'s IT. Covers the Milestone 11 validation checklist:
 * CONFIRMED past end_datetime + 24h → MISSED, already-COMPLETED rows
 * excluded from the sweep, and idempotent rerun safety.
 */
@SpringBootTest
@TestPropertySource(properties = "booking.missed-appointment-job.initial-delay-ms=3600000")
class MissedAppointmentJobIT {

    private static final long GENERAL_CONSULT_TYPE_ID = 2L; // seeded by V2, §7.2
    private static final String LOCK_NAME = "missedAppointmentJob";
    private static final Calendar UTC_CALENDAR = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

    @Autowired
    private MissedAppointmentJob missedAppointmentJob;

    @Autowired
    private AppointmentAuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long providerId;

    @BeforeEach
    void seedProviderAndResetLock() {
        String email = "missed-job-it-" + UUID.randomUUID() + "@example.com";
        jdbcTemplate.update(
                "INSERT INTO providers (first_name, last_name, specialty, email, timezone, is_active) "
                        + "VALUES ('Test', 'Provider', 'General Medicine', ?, 'America/New_York', TRUE)",
                email);
        providerId = jdbcTemplate.queryForObject("SELECT id FROM providers WHERE email = ?", Long.class, email);
        upsertLock(Instant.now().minusSeconds(120), "reset");
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update(
                "DELETE FROM appointment_audit_log WHERE appointment_id IN "
                        + "(SELECT id FROM appointments WHERE provider_id = ?)", providerId);
        jdbcTemplate.update("DELETE FROM appointments WHERE provider_id = ?", providerId);
        jdbcTemplate.update("DELETE FROM providers WHERE id = ?", providerId);
    }

    @Test
    void marksOnlyOverdueConfirmedAppointmentsAsMissed_excludingAlreadyCompleted() {
        long overdueConfirmed = insertAppointment("CONFIRMED", Instant.now().minus(25, ChronoUnit.HOURS));
        long recentConfirmed = insertAppointment("CONFIRMED", Instant.now().minus(1, ChronoUnit.HOURS));
        long overdueCompleted = insertAppointment("COMPLETED", Instant.now().minus(48, ChronoUnit.HOURS));

        missedAppointmentJob.markMissedAppointments();

        assertThat(statusOf(overdueConfirmed)).isEqualTo("MISSED");
        assertThat(statusOf(recentConfirmed)).isEqualTo("CONFIRMED");
        assertThat(statusOf(overdueCompleted)).isEqualTo("COMPLETED");

        List<AppointmentAuditLog> rows = auditLogRepository.findAll().stream()
                .filter(row -> row.getAppointmentId().equals(overdueConfirmed))
                .toList();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getChangedBy()).isEqualTo("SYSTEM");
        assertThat(rows.get(0).getPreviousStatus()).isEqualTo("CONFIRMED");
        assertThat(rows.get(0).getNewStatus()).isEqualTo("MISSED");
    }

    @Test
    void isIdempotent_rerunningIsANoOp() {
        long overdueConfirmed = insertAppointment("CONFIRMED", Instant.now().minus(25, ChronoUnit.HOURS));

        missedAppointmentJob.markMissedAppointments();
        upsertLock(Instant.now().minusSeconds(120), "reset");
        missedAppointmentJob.markMissedAppointments();

        assertThat(statusOf(overdueConfirmed)).isEqualTo("MISSED");
        long auditRowCount = auditLogRepository.findAll().stream()
                .filter(row -> row.getAppointmentId().equals(overdueConfirmed))
                .count();
        assertThat(auditRowCount).isEqualTo(1);
    }

    private void upsertLock(Instant lockUntil, String lockedBy) {
        jdbcTemplate.update(
                "INSERT INTO shedlock (name, lock_until, locked_at, locked_by) VALUES (?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE lock_until = VALUES(lock_until), "
                        + "locked_at = VALUES(locked_at), locked_by = VALUES(locked_by)",
                LOCK_NAME, Timestamp.from(lockUntil), Timestamp.from(Instant.now()), lockedBy);
    }

    private String statusOf(long appointmentId) {
        return jdbcTemplate.queryForObject("SELECT status FROM appointments WHERE id = ?", String.class, appointmentId);
    }

    private long insertAppointment(String status, Instant endDatetime) {
        String token = UUID.randomUUID().toString();
        String idempotencyKey = UUID.randomUUID().toString();
        Instant start = endDatetime.minusSeconds(1800);
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO appointments (confirmation_token, provider_id, appointment_type_id, "
                            + "patient_full_name, patient_email, patient_phone, start_datetime, end_datetime, "
                            + "status, idempotency_key, request_body_hash) "
                            + "VALUES (?, ?, ?, 'Jordan Rivera', 'jordan@example.com', '+14155551234', ?, ?, "
                            + "?, ?, '0000000000000000000000000000000000000000000000000000000000000000')");
            ps.setString(1, token);
            ps.setLong(2, providerId);
            ps.setLong(3, GENERAL_CONSULT_TYPE_ID);
            ps.setTimestamp(4, Timestamp.from(start), UTC_CALENDAR);
            ps.setTimestamp(5, Timestamp.from(endDatetime), UTC_CALENDAR);
            ps.setString(6, status);
            ps.setString(7, idempotencyKey);
            return ps;
        });
        return jdbcTemplate.queryForObject("SELECT id FROM appointments WHERE confirmation_token = ?", Long.class, token);
    }
}
