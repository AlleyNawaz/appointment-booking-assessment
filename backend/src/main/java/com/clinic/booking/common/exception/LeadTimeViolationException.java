package com.clinic.booking.common.exception;

/** §11.2: {@code startDateTime} is less than {@code MIN_LEAD_TIME_HOURS} from now. */
public class LeadTimeViolationException extends RuntimeException {

    public LeadTimeViolationException() {
        super("Appointments must be booked at least the minimum lead time in advance.");
    }
}
