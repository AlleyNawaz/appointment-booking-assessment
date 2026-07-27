package com.clinic.booking.common.exception;

/** §11.6: patient identity has reached the daily active-appointment cap (1 per provider, 3 total). */
public class PatientDailyLimitExceededException extends RuntimeException {

    public PatientDailyLimitExceededException() {
        super("You have reached the maximum number of appointments allowed per day.");
    }
}
