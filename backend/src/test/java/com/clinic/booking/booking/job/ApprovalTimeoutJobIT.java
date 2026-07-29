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
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ApprovalTimeoutJob} (PRD §12.7/§12.11/AC-10)
 * against the real local MySQL schema — same pattern established by
 * {@link HoldReaperJob}'s IT (deterministic ShedLock simulation, no
 * Testcontainers). Covers the Milestone 11 validation checklist's AC-10 and
 * idempotent-rerun bullets.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "booking.approval-timeout-job.initial-delay-ms=3600000",
        // See HoldReaperJobIT's identical block: all three @Scheduled jobs default to
        // initial-delay-ms=0, so left at their defaults this context would also auto-fire
        // the other two jobs' real triggers at startup, racing this class's own shedlock
        // manipulation for a job it isn't testing.
        "booking.hold-reaper.initial-delay-ms=3600000",
        "booking.missed-appointment-job.initial-delay-ms=3600000"
})
class ApprovalTimeoutJobIT {

    private static final long GENERAL_CONSULT_TYPE_ID = 2L; // seeded by V2, §7.2
    private static final String LOCK_NAME = "approvalTimeoutJob";
    private static final Calendar UTC_CALENDAR = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

    @Autowired
    private ApprovalTimeoutJob approvalTimeoutJob;

    @Autowired
    private AppointmentAuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long providerId;
    private final java.util.concurrent.atomic.AtomicInteger sequence = new java.util.concurrent.atomic.AtomicInteger();

    @BeforeEach
    void seedProviderAndResetLock() {
        String email = "approval-timeout-it-" + UUID.randomUUID() + "@example.com";
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
    void expiresOnlyPendingAppointmentsOlderThanTheApprovalTimeout() {
        long overdue = insertPending(Instant.now().minus(25, java.time.temporal.ChronoUnit.HOURS));
        long recent = insertPending(Instant.now().minus(1, java.time.temporal.ChronoUnit.HOURS));

        approvalTimeoutJob.expirePendingApprovals();

        assertThat(statusOf(overdue)).isEqualTo("EXPIRED");
        assertThat(statusOf(recent)).isEqualTo("PENDING");

        List<AppointmentAuditLog> rows = auditLogRepository.findAll().stream()
                .filter(row -> row.getAppointmentId().equals(overdue))
                .toList();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getChangedBy()).isEqualTo("SYSTEM");
        assertThat(rows.get(0).getPreviousStatus()).isEqualTo("PENDING");
        assertThat(rows.get(0).getNewStatus()).isEqualTo("EXPIRED");
    }

    @Test
    void isIdempotent_rerunningIsANoOp() {
        long overdue = insertPending(Instant.now().minus(25, java.time.temporal.ChronoUnit.HOURS));

        approvalTimeoutJob.expirePendingApprovals();
        upsertLock(Instant.now().minusSeconds(120), "reset");
        approvalTimeoutJob.expirePendingApprovals();

        assertThat(statusOf(overdue)).isEqualTo("EXPIRED");
        long auditRowCount = auditLogRepository.findAll().stream()
                .filter(row -> row.getAppointmentId().equals(overdue))
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

    private long insertPending(Instant createdAt) {
        String token = UUID.randomUUID().toString();
        String idempotencyKey = UUID.randomUUID().toString();
        Instant start = Instant.now().plusSeconds(3600L * 24 * 30 + sequence.getAndIncrement() * 60L);
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO appointments (confirmation_token, provider_id, appointment_type_id, "
                            + "patient_full_name, patient_email, patient_phone, start_datetime, end_datetime, "
                            + "status, idempotency_key, request_body_hash, created_at) "
                            + "VALUES (?, ?, ?, 'Jordan Rivera', 'jordan@example.com', '+14155551234', ?, ?, "
                            + "'PENDING', ?, '0000000000000000000000000000000000000000000000000000000000000000', ?)");
            ps.setString(1, token);
            ps.setLong(2, providerId);
            ps.setLong(3, GENERAL_CONSULT_TYPE_ID);
            ps.setTimestamp(4, Timestamp.from(start), UTC_CALENDAR);
            ps.setTimestamp(5, Timestamp.from(start.plusSeconds(1800)), UTC_CALENDAR);
            ps.setString(6, idempotencyKey);
            ps.setTimestamp(7, Timestamp.from(createdAt), UTC_CALENDAR);
            return ps;
        });
        return jdbcTemplate.queryForObject("SELECT id FROM appointments WHERE confirmation_token = ?", Long.class, token);
    }
}
