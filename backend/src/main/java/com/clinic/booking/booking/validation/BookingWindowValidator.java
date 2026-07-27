package com.clinic.booking.booking.validation;

import com.clinic.booking.common.exception.BookingWindowExceededException;
import com.clinic.booking.config.BookingProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** §11.3: {@code startDateTime} must be within {@code MAX_BOOKING_WINDOW_DAYS} of today (clinic timezone). */
@Component
public class BookingWindowValidator {

    private final BookingProperties bookingProperties;

    public BookingWindowValidator(BookingProperties bookingProperties) {
        this.bookingProperties = bookingProperties;
    }

    public void validate(Instant startDateTime) {
        ZoneId clinicZone = ZoneId.of(bookingProperties.getClinicTimezone());
        LocalDate requestedDate = ZonedDateTime.ofInstant(startDateTime, clinicZone).toLocalDate();
        LocalDate today = LocalDate.now(clinicZone);
        if (requestedDate.isAfter(today.plusDays(bookingProperties.getMaxBookingWindowDays()))) {
            throw new BookingWindowExceededException();
        }
    }
}
