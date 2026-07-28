package com.clinic.booking.staff.dto;

import com.clinic.booking.booking.domain.ClinicHoliday;

import java.time.LocalDate;

/** Response shape for the clinic-holiday endpoints (PRD §8.16). */
public record HolidayResponse(Long id, LocalDate holidayDate, String name, boolean isRecurringAnnually) {

    public static HolidayResponse from(ClinicHoliday holiday) {
        return new HolidayResponse(holiday.getId(), holiday.getHolidayDate(), holiday.getName(),
                holiday.isRecurringAnnually());
    }
}
