package com.clinic.booking.common.exception;

/** §8.6: {@code Idempotency-Key} was seen before, but the request body hash no longer matches. */
public class IdempotencyKeyReusedMismatchException extends RuntimeException {

    public IdempotencyKeyReusedMismatchException() {
        super("This idempotency key was already used with a different request body.");
    }
}
