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
 * PRD §12.7/§19 #25: a nightly sweep marks a still-{@code CONFIRMED} appointment
 * {@code MISSED} once its {@code end_datetime} is 24h+ in the past. The
 * {@code WHERE status = 'CONFIRMED'} guard (via the repository finder) excludes
 * rows a staff member already marked {@code COMPLETED} moments before this runs
 * — no race with the manual action. Idempotent/ShedLock-protected exactly like
 * {@link ApprovalTimeoutJob}/{@link HoldReaperJob}.
 */
@Component
public class MissedAppointmentJob {

    private final AppointmentRepository appointmentRepository;
    private final AuditLogWriter auditLogWriter;
    private final BookingProperties bookingProperties;

    public MissedAppointmentJob(
            AppointmentRepository appointmentRepository,
            AuditLogWriter auditLogWriter,
            BookingProperties bookingProperties) {
        this.appointmentRepository = appointmentRepository;
        this.auditLogWriter = auditLogWriter;
        this.bookingProperties = bookingProperties;
    }

    @Scheduled(
            initialDelayString = "${booking.missed-appointment-job.initial-delay-ms:0}",
            fixedRateString = "${booking.missed-appointment-job.fixed-rate-ms:86400000}")
    // §12.14: lockAtLeastFor matches this job's own 24h interval. ShedLock requires
    // lockAtMostFor >= lockAtLeastFor, so it moves to 25h (still comfortably above the
    // job's actual runtime) purely to satisfy that constraint, not because the job runs longer.
    @SchedulerLock(name = "missedAppointmentJob", lockAtLeastFor = "PT24H", lockAtMostFor = "PT25H")
    @Transactional
    public void markMissedAppointments() {
        Instant cutoff = Instant.now().minus(bookingProperties.getMissedMarkerGraceHours(), ChronoUnit.HOURS);
        List<Appointment> candidates =
                appointmentRepository.findByStatusAndEndDatetimeBefore(Appointment.Status.CONFIRMED, cutoff);
        for (Appointment appointment : candidates) {
            appointment.markMissed();
            auditLogWriter.write(
                    appointment.getId(), Appointment.Status.CONFIRMED, Appointment.Status.MISSED, "SYSTEM", null);
        }
    }
}
