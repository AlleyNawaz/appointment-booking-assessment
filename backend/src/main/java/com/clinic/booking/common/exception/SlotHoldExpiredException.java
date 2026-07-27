package com.clinic.booking.common.exception;

/** §8.6/§12.10: the {@code slot_holds} row for the supplied {@code holdToken} is missing or expired. */
public class SlotHoldExpiredException extends RuntimeException {

    public SlotHoldExpiredException() {
        super("That time slot was only held for 5 minutes and has been released — please pick a time again.");
    }
}
