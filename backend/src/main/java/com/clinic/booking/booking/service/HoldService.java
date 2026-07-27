package com.clinic.booking.booking.service;

import com.clinic.booking.booking.domain.AppointmentType;
import com.clinic.booking.booking.domain.Provider;
import com.clinic.booking.booking.domain.SlotHold;
import com.clinic.booking.booking.dto.HoldResponse;
import com.clinic.booking.booking.repository.AppointmentTypeRepository;
import com.clinic.booking.booking.repository.ProviderRepository;
import com.clinic.booking.booking.repository.SlotHoldRepository;
import com.clinic.booking.common.exception.ProviderUnavailableException;
import com.clinic.booking.common.exception.SlotAlreadyBookedException;
import com.clinic.booking.config.BookingProperties;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Implements POST /booking/holds (PRD §8.5) — the soft, UX-layer lock (§12.10).
 * §8.5 names exactly one error for this endpoint, {@code SLOT_ALREADY_BOOKED};
 * all other §11 validation (lead time, booking window, weekend/holiday) is
 * deliberately not enforced here — it belongs to booking creation (§8.6,
 * Milestone 5), which is the only place the PRD's error-code list mentions it.
 */
@Service
public class HoldService {

    private final ProviderRepository providerRepository;
    private final AppointmentTypeRepository appointmentTypeRepository;
    private final SlotHoldRepository slotHoldRepository;
    private final BookingProperties bookingProperties;

    public HoldService(
            ProviderRepository providerRepository,
            AppointmentTypeRepository appointmentTypeRepository,
            SlotHoldRepository slotHoldRepository,
            BookingProperties bookingProperties) {
        this.providerRepository = providerRepository;
        this.appointmentTypeRepository = appointmentTypeRepository;
        this.slotHoldRepository = slotHoldRepository;
        this.bookingProperties = bookingProperties;
    }

    public HoldResponse createHold(Long providerId, Long appointmentTypeId, Instant startDateTime) {
        Provider provider = providerRepository.findById(providerId)
                .filter(p -> p.isActive() && p.getDeletedAt() == null)
                .orElseThrow(ProviderUnavailableException::new);
        AppointmentType appointmentType = appointmentTypeRepository.findById(appointmentTypeId)
                .filter(AppointmentType::isActive)
                .orElseThrow(ProviderUnavailableException::new);

        Instant endDateTime = startDateTime.plus(
                appointmentType.getDurationMinutes() + appointmentType.getBufferMinutes(), ChronoUnit.MINUTES);
        Instant expiresAt = Instant.now().plus(bookingProperties.getHoldDurationMinutes(), ChronoUnit.MINUTES);
        String holdToken = UUID.randomUUID().toString();

        SlotHold hold = new SlotHold(
                provider.getId(), appointmentType.getId(), startDateTime, endDateTime, holdToken, expiresAt);

        try {
            slotHoldRepository.saveAndFlush(hold);
        } catch (DataIntegrityViolationException e) {
            // §10: the insert attempt is the concurrency check, not a pre-check-then-insert —
            // violating uq_hold_slot(provider_id, start_datetime) means someone else won the race.
            // Hibernate's JPA exception translation maps every constraint violation (unique, FK,
            // ...) to this same generic exception type, so the specific constraint name is
            // checked before mapping to SLOT_ALREADY_BOOKED — an unrelated integrity failure
            // must never be silently mislabeled as this conflict and hidden from diagnosis.
            if (isUniqueSlotConstraintViolation(e)) {
                throw new SlotAlreadyBookedException();
            }
            throw e;
        }

        return new HoldResponse(holdToken, expiresAt);
    }

    private static boolean isUniqueSlotConstraintViolation(DataIntegrityViolationException e) {
        if (!(e.getCause() instanceof ConstraintViolationException cve)) {
            return false;
        }
        String constraintName = cve.getConstraintName();
        return constraintName != null && constraintName.toLowerCase().contains("uq_hold_slot");
    }
}
