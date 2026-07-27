package com.clinic.booking.booking.validation;

import com.clinic.booking.common.exception.InvalidAppointmentDateException;
import com.clinic.booking.common.exception.LeadTimeViolationException;
import com.clinic.booking.config.BookingProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * §11.1 (no past dates, {@code INVALID_APPOINTMENT_DATE}) and §11.2 (minimum
 * {@code MIN_LEAD_TIME_HOURS} lead time, {@code LEAD_TIME_VIOLATION}) — both
 * operate on the same now-vs-{@code startDateTime} comparison, so one
 * validator owns both, checking the more specific "already in the past" case
 * first.
 */
@Component
public class LeadTimeValidator {

    private final BookingProperties bookingProperties;

    public LeadTimeValidator(BookingProperties bookingProperties) {
        this.bookingProperties = bookingProperties;
    }

    public void validate(Instant startDateTime) {
        Instant now = Instant.now();
        if (startDateTime.isBefore(now)) {
            throw new InvalidAppointmentDateException();
        }
        Instant earliestAllowed = now.plus(bookingProperties.getMinLeadTimeHours(), ChronoUnit.HOURS);
        if (startDateTime.isBefore(earliestAllowed)) {
            throw new LeadTimeViolationException();
        }
    }
}
