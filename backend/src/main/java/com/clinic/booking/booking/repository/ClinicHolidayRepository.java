package com.clinic.booking.booking.repository;

import com.clinic.booking.booking.domain.ClinicHoliday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface ClinicHolidayRepository extends JpaRepository<ClinicHoliday, Long> {

    /** §11.5/§12.4: an exact-date match blocks every provider unconditionally. */
    boolean existsByHolidayDate(LocalDate holidayDate);
}
