package com.clinic.booking.common.exception;

/**
 * §8.20/§15.9: {@code locked_until} is still in the future — every login
 * attempt fails this way while locked, even with the correct password.
 */
public class AccountLockedException extends RuntimeException {

    public AccountLockedException() {
        super("This account is temporarily locked due to too many failed login attempts. Please try again later.");
    }
}
