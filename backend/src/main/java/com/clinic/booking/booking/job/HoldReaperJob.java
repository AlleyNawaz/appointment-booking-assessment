package com.clinic.booking.booking.job;

import com.clinic.booking.booking.repository.SlotHoldRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * PRD §7.8: "A scheduled job (@Scheduled(fixedRate = 60000)) deletes rows
 * where expires_at &lt; NOW()." Idempotent and state-based (§14) — a missed or
 * repeated run simply matches zero additional rows, never double-expires
 * anything. {@code @SchedulerLock} (§12.14/§7.13) guarantees exactly one
 * application instance executes a given tick in a horizontally-scaled
 * deployment; {@code lockAtLeastFor} matches the job's own fixed interval so
 * two back-to-back ticks on a fast instance can't double-acquire within the
 * same logical period.
 *
 * <p>The rate and initial delay are externalized as properties (defaulting to
 * the PRD's literal {@code fixedRate = 60000} with no initial delay) purely so
 * integration tests can push the automatic trigger far out — otherwise it
 * fires immediately at context startup, acquires the ShedLock lock, and holds
 * it for {@code lockAtLeastFor}, silently skipping a test's own direct calls
 * to this method within that window.
 */
@Component
public class HoldReaperJob {

    private final SlotHoldRepository slotHoldRepository;

    public HoldReaperJob(SlotHoldRepository slotHoldRepository) {
        this.slotHoldRepository = slotHoldRepository;
    }

    @Scheduled(
            initialDelayString = "${booking.hold-reaper.initial-delay-ms:0}",
            fixedRateString = "${booking.hold-reaper.fixed-rate-ms:60000}")
    @SchedulerLock(name = "holdReaperJob", lockAtLeastFor = "PT1M", lockAtMostFor = "PT2M")
    @Transactional
    public void reapExpiredHolds() {
        slotHoldRepository.deleteByExpiresAtBefore(Instant.now());
    }
}
