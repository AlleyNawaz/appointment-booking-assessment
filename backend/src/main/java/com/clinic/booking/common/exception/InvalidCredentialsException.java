package com.clinic.booking.common.exception;

/**
 * §8.20/§15.9: an unknown username and a wrong password against a known one
 * both throw this — identical response shape, so the login endpoint can't be
 * used to enumerate valid usernames (the same anti-oracle principle as
 * §8.7/§15.3, applied to usernames instead of confirmation tokens).
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid username or password.");
    }
}
