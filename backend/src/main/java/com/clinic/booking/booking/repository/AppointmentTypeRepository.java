package com.clinic.booking.booking.repository;

import com.clinic.booking.booking.domain.AppointmentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppointmentTypeRepository extends JpaRepository<AppointmentType, Long> {

    /**
     * Active appointment types only — GET /booking/appointment-types (§8.2) is a
     * patient-facing selection list, so deactivated types are never offered.
     */
    List<AppointmentType> findByActiveTrueOrderById();

    /** §8.12: GET /staff/appointment-types includes inactive types too. */
    List<AppointmentType> findAllByOrderById();

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);
}
