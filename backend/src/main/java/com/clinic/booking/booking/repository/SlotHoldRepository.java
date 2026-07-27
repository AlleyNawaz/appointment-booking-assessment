package com.clinic.booking.booking.repository;

import com.clinic.booking.booking.domain.SlotHold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SlotHoldRepository extends JpaRepository<SlotHold, Long> {

    /** §8.6: loads the hold consumed at booking-creation time to resolve providerId/appointmentTypeId. */
    Optional<SlotHold> findByHoldToken(String holdToken);

    /**
     * §7.8/§14: deletes rows where {@code expires_at < threshold} — the hold
     * reaper's cleanup query. State-based (not counter-based), so re-running it
     * is always idempotent: a hold already deleted simply matches zero rows.
     */
    long deleteByExpiresAtBefore(Instant threshold);

    /**
     * Active (not yet expired) holds for a provider overlapping [rangeStart, rangeEnd)
     * — the "active slot_holds" union member in §8.4's availability computation.
     */
    @Query("SELECT h FROM SlotHold h WHERE h.providerId = :providerId "
            + "AND h.startDatetime < :rangeEnd AND h.endDatetime > :rangeStart AND h.expiresAt > :now")
    List<SlotHold> findActiveOverlapping(
            @Param("providerId") Long providerId,
            @Param("rangeStart") Instant rangeStart,
            @Param("rangeEnd") Instant rangeEnd,
            @Param("now") Instant now);
}
