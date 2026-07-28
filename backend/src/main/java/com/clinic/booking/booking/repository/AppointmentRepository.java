package com.clinic.booking.booking.repository;

import com.clinic.booking.booking.domain.Appointment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    /** §8.6 idempotency lookup — step (1) of the replay contract. */
    Optional<Appointment> findByIdempotencyKey(String idempotencyKey);

    /** §8.7/§8.8: token-based lookup for the never-gated patient self-service endpoints. */
    Optional<Appointment> findByConfirmationToken(String confirmationToken);

    /**
     * Active-status appointments for a patient identity (§11.6:
     * {@code lower(email) + phone}) whose start falls on the given calendar
     * day — used for both the same-provider-per-day and
     * across-all-providers-per-day limits. {@code activeStatuses} is passed
     * in by the caller ({@code PENDING}/{@code CONFIRMED}) rather than
     * embedded here as a JPQL enum literal.
     */
    @Query("SELECT a FROM Appointment a WHERE LOWER(a.patientEmail) = LOWER(:email) AND a.patientPhone = :phone "
            + "AND a.status IN :activeStatuses AND a.startDatetime >= :dayStart AND a.startDatetime < :dayEnd")
    List<Appointment> findActiveForPatientOnDay(
            @Param("email") String email,
            @Param("phone") String phone,
            @Param("activeStatuses") Collection<Appointment.Status> activeStatuses,
            @Param("dayStart") Instant dayStart,
            @Param("dayEnd") Instant dayEnd);

    /**
     * §8.4: active (PENDING/CONFIRMED) appointments for a provider overlapping
     * [rangeStart, rangeEnd) — the "existing appointments" union member in
     * availability computation. {@code end_datetime} already includes buffer
     * (§7.7), so no separate buffer arithmetic is needed here.
     */
    @Query("SELECT a FROM Appointment a WHERE a.providerId = :providerId AND a.status IN :activeStatuses "
            + "AND a.startDatetime < :rangeEnd AND a.endDatetime > :rangeStart")
    List<Appointment> findActiveOverlapping(
            @Param("providerId") Long providerId,
            @Param("activeStatuses") Collection<Appointment.Status> activeStatuses,
            @Param("rangeStart") Instant rangeStart,
            @Param("rangeEnd") Instant rangeEnd);

    /**
     * §11.7 duplicate-prevention: does this patient identity already hold an
     * active appointment with this provider overlapping the requested range?
     */
    @Query("SELECT COUNT(a) > 0 FROM Appointment a WHERE LOWER(a.patientEmail) = LOWER(:email) "
            + "AND a.patientPhone = :phone AND a.providerId = :providerId AND a.status IN :activeStatuses "
            + "AND a.startDatetime < :rangeEnd AND a.endDatetime > :rangeStart")
    boolean existsActiveOverlapping(
            @Param("email") String email,
            @Param("phone") String phone,
            @Param("providerId") Long providerId,
            @Param("activeStatuses") Collection<Appointment.Status> activeStatuses,
            @Param("rangeStart") Instant rangeStart,
            @Param("rangeEnd") Instant rangeEnd);

    /**
     * §8.9: staff console list — every filter is optional (bound {@code null}
     * skips that predicate), {@code to} is exclusive on {@code startDatetime}.
     */
    @Query("SELECT a FROM Appointment a WHERE (:status IS NULL OR a.status = :status) "
            + "AND (:providerId IS NULL OR a.providerId = :providerId) "
            + "AND (:from IS NULL OR a.startDatetime >= :from) "
            + "AND (:to IS NULL OR a.startDatetime < :to)")
    Page<Appointment> search(
            @Param("status") Appointment.Status status,
            @Param("providerId") Long providerId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    /** §12.7/§12.11: the approval-timeout job's candidate rows — PENDING with no staff action for 24h+. */
    List<Appointment> findByStatusAndCreatedAtBefore(Appointment.Status status, Instant cutoff);

    /**
     * §12.7/§19 #25: the nightly missed-marker job's candidate rows — still {@code CONFIRMED}
     * (excludes rows a staff member already completed) with {@code end_datetime} 24h+ in the past.
     */
    List<Appointment> findByStatusAndEndDatetimeBefore(Appointment.Status status, Instant cutoff);
}
