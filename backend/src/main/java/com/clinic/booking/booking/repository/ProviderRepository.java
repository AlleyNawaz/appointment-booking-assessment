package com.clinic.booking.booking.repository;

import com.clinic.booking.booking.domain.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProviderRepository extends JpaRepository<Provider, Long> {

    /**
     * Active, non-deleted providers offering the given appointment type —
     * GET /booking/providers?appointmentTypeId= (§8.3). A type that doesn't exist
     * or is inactive simply matches no providers, returning an empty list rather
     * than an error (mirrors the empty-result-is-valid precedent in §19 #35).
     */
    @Query("SELECT DISTINCT p FROM Provider p JOIN p.appointmentTypes t "
            + "WHERE t.id = :appointmentTypeId AND p.active = true AND p.deletedAt IS NULL "
            + "ORDER BY p.id")
    List<Provider> findActiveByAppointmentTypeId(@Param("appointmentTypeId") Long appointmentTypeId);

    /** §8.13: GET /staff/providers includes inactive/soft-deleted providers too. */
    List<Provider> findAllByOrderById();

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);
}
