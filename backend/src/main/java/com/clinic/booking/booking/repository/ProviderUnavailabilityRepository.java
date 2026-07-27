package com.clinic.booking.booking.repository;

import com.clinic.booking.booking.domain.ProviderUnavailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ProviderUnavailabilityRepository extends JpaRepository<ProviderUnavailability, Long> {

    /**
     * Unavailability rows for the given provider overlapping [rangeStart, rangeEnd)
     * — two ranges [a,b) and [c,d) overlap iff a &lt; d and c &lt; b. Used by §8.4's
     * availability computation to subtract vacation/sick-leave/emergency-closure
     * time (§7.5/§12.2/§12.3) from a single requested calendar date.
     */
    @Query("SELECT u FROM ProviderUnavailability u WHERE u.providerId = :providerId "
            + "AND u.startDatetime < :rangeEnd AND u.endDatetime > :rangeStart")
    List<ProviderUnavailability> findOverlapping(
            @Param("providerId") Long providerId,
            @Param("rangeStart") Instant rangeStart,
            @Param("rangeEnd") Instant rangeEnd);
}
