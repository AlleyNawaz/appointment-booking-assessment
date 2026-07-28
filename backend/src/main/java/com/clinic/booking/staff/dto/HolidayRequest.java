package com.clinic.booking.staff.dto;

import java.time.LocalDate;

/** Request body for POST/PUT /staff/holidays (PRD §8.16). */
public record HolidayRequest(LocalDate holidayDate, String name, Boolean isRecurringAnnually) {
}
