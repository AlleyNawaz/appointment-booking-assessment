package com.clinic.booking.common.exception;

/**
 * §8.8/§12.6: cancellation is inside the {@code CANCELLATION_CUTOFF_HOURS}
 * window (or the appointment is not in a self-cancellable state — no
 * diagram edge exists for a patient cancelling anything but a
 * {@code CONFIRMED} appointment, §12.7). The message must carry the clinic
 * phone number so the patient knows how to reach staff instead.
 */
public class CancellationWindowExpiredException extends RuntimeException {

    public CancellationWindowExpiredException(String clinicPhoneNumber) {
        super("This appointment can no longer be cancelled online. Please call the clinic at "
                + clinicPhoneNumber + " for assistance.");
    }
}
