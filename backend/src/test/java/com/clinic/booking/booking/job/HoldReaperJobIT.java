package com.clinic.booking.booking.job;

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
import java.util.TimeZone;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link HoldReaperJob} (PRD §7.8/§12.14/§14) against
 * the real local MySQL schema, including a deterministic simulation of a
 * second application instance already holding the ShedLock lock (rather than
 * a timing-dependent multi-thread race, which would be flaky).
 *
 * <p>{@code booking.hold-reaper.initial-delay-ms} is pushed out to an hour so
 * this test class's own Spring context doesn't fire the real automatic
 * trigger during the test run — that trigger fires immediately at context
 * startup by default, which would otherwise acquire the same ShedLock lock
 * these tests manipulate directly and race with them.
 *
 * <p>{@code JdbcTemplateLockProvider} (via {@code StorageBasedLockProvider})
 * keeps an in-memory {@code LockRecordRegistry} for the lifetime of the lock
 * provider bean: once a lock row has been inserted for a given name, it never
 * attempts another {@code INSERT} for that name again, only ever an
 * {@code UPDATE ... WHERE lock_until <= NOW()}. Deleting the {@code shedlock}
 * row between tests (as this class used to do) therefore breaks lock
 * re-acquisition — the registry still believes the row exists, so no
 * {@code INSERT} is attempted, and the {@code UPDATE} matches nothing. Each
 * test instead resets the row in place (upsert to an already-expired
 * {@code lock_until}) so it always exists for the {@code UPDATE} path to find.
 */
@SpringBootTest
@TestPropertySource(properties = "booking.hold-reaper.initial-delay-ms=3600000")
class HoldReaperJobIT {

    private static final long GENERAL_CONSULT_TYPE_ID = 2L; // seeded by V2, §7.2
    private static final String LOCK_NAME = "holdReaperJob";

    @Autowired
    private HoldReaperJob holdReaperJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long providerId;

    @BeforeEach
    void seedTestProviderAndResetLock() {
        String email = "hold-reaper-it-" + UUID.randomUUID() + "@example.com";
        jdbcTemplate.update(
                "INSERT INTO providers (first_name, last_name, specialty, email, timezone, is_active) "
                        + "VALUES ('Test', 'Provider', 'General Medicine', ?, 'America/New_York', TRUE)",
                email);
        providerId = jdbcTemplate.queryForObject("SELECT id FROM providers WHERE email = ?", Long.class, email);

        resetLockToExpired();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM slot_holds WHERE provider_id = ?", providerId);
        jdbcTemplate.update("DELETE FROM providers WHERE id = ?", providerId);
    }

    @Test
    void reaper_deletesOnlyExpiredHolds() {
        insertHold(Instant.now().minusSeconds(60)); // expired
        insertHold(Instant.now().plusSeconds(300)); // still active

        holdReaperJob.reapExpiredHolds();

        assertThat(remainingHoldCount()).isEqualTo(1);
    }

    @Test
    void reaper_isIdempotent_rerunningIsANoOp() {
        // The underlying DELETE is state-based (§14): a second run either re-executes and
        // matches zero rows, or is skipped outright by ShedLock's lockAtLeastFor (§12.14) —
        // both are a safe no-op, which is exactly what "idempotent" means here. Production
        // code only ever calls this method (never the repository directly), so exercising
        // the real method twice is the most faithful test of the actual guarantee.
        insertHold(Instant.now().minusSeconds(60));

        holdReaperJob.reapExpiredHolds();
        resetLockToExpired();
        holdReaperJob.reapExpiredHolds();

        assertThat(remainingHoldCount()).isZero();
    }

    @Test
    void reaper_skipsExecution_whenAnotherInstanceAlreadyHoldsTheLock() {
        insertHold(Instant.now().minusSeconds(60));
        // Simulate a second application instance currently running this same tick.
        upsertLock(Instant.now().plusSeconds(120), "other-instance");

        holdReaperJob.reapExpiredHolds();

        // Still present: execution was skipped because the lock was unavailable, not because
        // the delete query itself failed to match it.
        assertThat(remainingHoldCount()).isEqualTo(1);
    }

    private void resetLockToExpired() {
        upsertLock(Instant.now().minusSeconds(120), "reset");
    }

    private void upsertLock(Instant lockUntil, String lockedBy) {
        jdbcTemplate.update(
                "INSERT INTO shedlock (name, lock_until, locked_at, locked_by) VALUES (?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE lock_until = VALUES(lock_until), "
                        + "locked_at = VALUES(locked_at), locked_by = VALUES(locked_by)",
                LOCK_NAME, Timestamp.from(lockUntil), Timestamp.from(Instant.now()), lockedBy);
    }

    private static final Calendar UTC_CALENDAR = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

    /**
     * A plain {@code Timestamp.from(instant)} passed through {@code JdbcTemplate}'s simple
     * varargs {@code update(sql, args...)} is written by the MySQL driver using the JVM's
     * default timezone's wall-clock fields (confirmed empirically on this machine,
     * Asia/Karachi, UTC+5) — but {@code slot_holds.expires_at} is read back through
     * Hibernate, which (per {@code hibernate.jdbc.time_zone: UTC}) writes and reads using
     * UTC wall-clock fields instead. Since {@code DATETIME} carries no timezone of its own,
     * mixing the two conventions for the same column produces a real, silent 5-hour
     * discrepancy. Passing an explicit UTC {@link Calendar} to {@code setTimestamp} is the
     * documented JDBC mechanism for pinning the wall-clock conversion to a specific zone,
     * making this raw insert agree with Hibernate's convention for the same column.
     */
    private void insertHold(Instant expiresAt) {
        Instant start = Instant.now().plusSeconds(3600);
        String holdToken = UUID.randomUUID().toString();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO slot_holds (provider_id, appointment_type_id, start_datetime, end_datetime, hold_token, expires_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?)");
            ps.setLong(1, providerId);
            ps.setLong(2, GENERAL_CONSULT_TYPE_ID);
            ps.setTimestamp(3, Timestamp.from(start), UTC_CALENDAR);
            ps.setTimestamp(4, Timestamp.from(start.plusSeconds(900)), UTC_CALENDAR);
            ps.setString(5, holdToken);
            ps.setTimestamp(6, Timestamp.from(expiresAt), UTC_CALENDAR);
            return ps;
        });
    }

    private Integer remainingHoldCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM slot_holds WHERE provider_id = ?", Integer.class, providerId);
    }
}
