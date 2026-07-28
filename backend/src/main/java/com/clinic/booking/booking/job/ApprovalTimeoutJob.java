package com.clinic.booking.booking.job;

import com.clinic.booking.audit.AuditLogWriter;
import com.clinic.booking.booking.domain.Appointment;
import com.clinic.booking.booking.repository.AppointmentRepository;
import com.clinic.booking.config.BookingProperties;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * PRD §12.7/§12.11/AC-10: a {@code PENDING} appointment with no staff action for
 * {@code APPROVAL_TIMEOUT_HOURS} (24h) auto-expires. Idempotent and state-based
 * (§14) — a missed or repeated run simply matches zero additional rows, never
 * double-expires anything. {@code @SchedulerLock} (§12.14/§7.13) guarantees
 * exactly one instance executes a given tick in a horizontally-scaled deployment.
 *
 * <p>Same test-injectability pattern as {@link HoldReaperJob}: rate/delay are
 * externalized properties so integration tests can push the automatic trigger
 * far out and call the method directly instead.
 */
@Component
public class ApprovalTimeoutJob {

    private final AppointmentRepository appointmentRepository;
    private final AuditLogWriter auditLogWriter;
    private final BookingProperties bookingProperties;

    public ApprovalTimeoutJob(
            AppointmentRepository appointmentRepository,
            AuditLogWriter auditLogWriter,
            BookingProperties bookingProperties) {
        this.appointmentRepository = appointmentRepository;
        this.auditLogWriter = auditLogWriter;
        this.bookingProperties = bookingProperties;
    }

    @Scheduled(
            initialDelayString = "${booking.approval-timeout-job.initial-delay-ms:0}",
            fixedRateString = "${booking.approval-timeout-job.fixed-rate-ms:86400000}")
    // §12.14: lockAtLeastFor matches this job's own 24h interval. ShedLock requires
    // lockAtMostFor >= lockAtLeastFor, so it moves to 25h (still comfortably above the
    // job's actual runtime) purely to satisfy that constraint, not because the job runs longer.
    @SchedulerLock(name = "approvalTimeoutJob", lockAtLeastFor = "PT24H", lockAtMostFor = "PT25H")
    @Transactional
    public void expirePendingApprovals() {
        Instant cutoff = Instant.now().minus(bookingProperties.getApprovalTimeoutHours(), ChronoUnit.HOURS);
        List<Appointment> candidates =
                appointmentRepository.findByStatusAndCreatedAtBefore(Appointment.Status.PENDING, cutoff);
        for (Appointment appointment : candidates) {
            appointment.expire();
            auditLogWriter.write(
                    appointment.getId(), Appointment.Status.PENDING, Appointment.Status.EXPIRED, "SYSTEM", null);
        }
    }
}
