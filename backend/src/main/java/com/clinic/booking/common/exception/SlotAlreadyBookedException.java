package com.clinic.booking.common.exception;

/**
 * Thrown when the unique constraint on {@code slot_holds(provider_id, start_datetime)}
 * is violated (PRD §8.5/§12.9/§12.10) — the insert attempt itself is the
 * concurrency check (§10), not a pre-check-then-insert. Mapped to
 * {@code 409 SLOT_ALREADY_BOOKED} by {@link GlobalExceptionHandler}.
 */
public class SlotAlreadyBookedException extends RuntimeException {

    public SlotAlreadyBookedException() {
        super("This time slot is no longer available.");
    }
}
