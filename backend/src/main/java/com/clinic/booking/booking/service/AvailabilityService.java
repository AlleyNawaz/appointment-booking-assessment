package com.clinic.booking.booking.service;

import com.clinic.booking.booking.domain.Appointment;
import com.clinic.booking.booking.domain.AppointmentType;
import com.clinic.booking.booking.domain.Provider;
import com.clinic.booking.booking.domain.ProviderAvailabilityRule;
import com.clinic.booking.booking.domain.ProviderAvailabilityRule.RuleType;
import com.clinic.booking.booking.dto.AvailabilityResponse;
import com.clinic.booking.booking.repository.AppointmentRepository;
import com.clinic.booking.booking.repository.AppointmentTypeRepository;
import com.clinic.booking.booking.repository.ClinicHolidayRepository;
import com.clinic.booking.booking.repository.ProviderAvailabilityRuleRepository;
import com.clinic.booking.booking.repository.ProviderRepository;
import com.clinic.booking.booking.repository.ProviderUnavailabilityRepository;
import com.clinic.booking.booking.repository.SlotHoldRepository;
import com.clinic.booking.common.exception.BookingWindowExceededException;
import com.clinic.booking.common.exception.InvalidAppointmentDateException;
import com.clinic.booking.config.BookingProperties;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Computes open slots for GET /booking/availability (PRD §8.4): a 15-minute
 * grid, in the provider's timezone, over WORKING minus BREAK for the
 * requested calendar date, minus existing CONFIRMED/PENDING appointments,
 * provider_unavailability ranges, and active slot_holds, with clinic_holidays
 * blocking the entire date unconditionally (§11.5).
 */
@Service
public class AvailabilityService {

    private static final int MINUTES_PER_DAY = 24 * 60;
    private static final List<Appointment.Status> ACTIVE_STATUSES =
            List.of(Appointment.Status.PENDING, Appointment.Status.CONFIRMED);

    private final ProviderRepository providerRepository;
    private final AppointmentTypeRepository appointmentTypeRepository;
    private final ProviderAvailabilityRuleRepository availabilityRuleRepository;
    private final ProviderUnavailabilityRepository unavailabilityRepository;
    private final SlotHoldRepository slotHoldRepository;
    private final AppointmentRepository appointmentRepository;
    private final ClinicHolidayRepository clinicHolidayRepository;
    private final BookingProperties bookingProperties;
    private final Timer availabilityLatencyTimer;

    public AvailabilityService(
            ProviderRepository providerRepository,
            AppointmentTypeRepository appointmentTypeRepository,
            ProviderAvailabilityRuleRepository availabilityRuleRepository,
            ProviderUnavailabilityRepository unavailabilityRepository,
            SlotHoldRepository slotHoldRepository,
            AppointmentRepository appointmentRepository,
            ClinicHolidayRepository clinicHolidayRepository,
            BookingProperties bookingProperties,
            @Qualifier("availabilityLatencyTimer") Timer availabilityLatencyTimer) {
        this.providerRepository = providerRepository;
        this.appointmentTypeRepository = appointmentTypeRepository;
        this.availabilityRuleRepository = availabilityRuleRepository;
        this.unavailabilityRepository = unavailabilityRepository;
        this.slotHoldRepository = slotHoldRepository;
        this.appointmentRepository = appointmentRepository;
        this.clinicHolidayRepository = clinicHolidayRepository;
        this.bookingProperties = bookingProperties;
        this.availabilityLatencyTimer = availabilityLatencyTimer;
    }

    /** PRD §14: records slot-computation latency regardless of which path below returns/throws. */
    public AvailabilityResponse computeAvailableSlots(Long providerId, Long appointmentTypeId, LocalDate date) {
        Timer.Sample sample = Timer.start();
        try {
            return doComputeAvailableSlots(providerId, appointmentTypeId, date);
        } finally {
            sample.stop(availabilityLatencyTimer);
        }
    }

    private AvailabilityResponse doComputeAvailableSlots(Long providerId, Long appointmentTypeId, LocalDate date) {
        validateDateWindow(date);

        // §11.5/§12.4: a clinic holiday blocks every provider unconditionally, no override.
        if (clinicHolidayRepository.existsByHolidayDate(date)) {
            return new AvailabilityResponse(date, List.of());
        }

        Optional<Provider> providerOpt = providerRepository.findById(providerId);
        Optional<AppointmentType> typeOpt = appointmentTypeRepository.findById(appointmentTypeId);
        // Unknown/inactive provider or type: no slots can exist for it. An empty result,
        // not an invented error, mirrors the precedent already established in §8.3 (M2).
        if (providerOpt.isEmpty() || typeOpt.isEmpty()) {
            return new AvailabilityResponse(date, List.of());
        }

        Provider provider = providerOpt.get();
        AppointmentType appointmentType = typeOpt.get();
        ZoneId providerZone = ZoneId.of(provider.getTimezone());

        int dayOfWeek = date.getDayOfWeek().getValue() % 7; // Mon=1..Sun=7 -> Sun=0..Sat=6, per §7.4
        List<ProviderAvailabilityRule> rules =
                availabilityRuleRepository.findByProviderIdAndDayOfWeek(providerId, dayOfWeek);

        List<MinuteRange> working = new ArrayList<>();
        List<MinuteRange> busy = new ArrayList<>();
        for (ProviderAvailabilityRule rule : rules) {
            MinuteRange range = new MinuteRange(toMinutes(rule.getStartTime()), toMinutes(rule.getEndTime()));
            if (rule.getRuleType() == RuleType.WORKING) {
                working.add(range);
            } else {
                busy.add(range);
            }
        }

        // §19 #35: a day with no WORKING rule at all (e.g. a weekend with no override) is a
        // valid, expected empty result, not an error.
        if (working.isEmpty()) {
            return new AvailabilityResponse(date, List.of());
        }

        Instant dayStart = date.atStartOfDay(providerZone).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(providerZone).toInstant();

        List<TimeRange> unavailabilityRanges = unavailabilityRepository.findOverlapping(providerId, dayStart, dayEnd)
                .stream()
                .map(u -> new TimeRange(u.getStartDatetime(), u.getEndDatetime()))
                .toList();
        List<TimeRange> holdRanges = slotHoldRepository
                .findActiveOverlapping(providerId, dayStart, dayEnd, Instant.now())
                .stream()
                .map(h -> new TimeRange(h.getStartDatetime(), h.getEndDatetime()))
                .toList();
        List<TimeRange> appointmentRanges = appointmentRepository
                .findActiveOverlapping(providerId, ACTIVE_STATUSES, dayStart, dayEnd)
                .stream()
                .map(a -> new TimeRange(a.getStartDatetime(), a.getEndDatetime()))
                .toList();

        busy.addAll(clipToDate(unavailabilityRanges, dayStart, dayEnd, providerZone));
        busy.addAll(clipToDate(holdRanges, dayStart, dayEnd, providerZone));
        busy.addAll(clipToDate(appointmentRanges, dayStart, dayEnd, providerZone));

        List<MinuteRange> open = working;
        for (MinuteRange cut : busy) {
            open = subtractFromAll(open, cut);
        }

        int totalMinutesNeeded = appointmentType.getDurationMinutes() + appointmentType.getBufferMinutes();
        int granularity = bookingProperties.getSlotGranularityMinutes();

        List<Instant> slots = new ArrayList<>();
        for (MinuteRange range : open) {
            int gridStart = ceilToGrid(range.start(), granularity);
            // §19 #36/#37: only offer a start time if the entire duration + buffer fits
            // before the next blocking interval — partial-fit slots are never offered.
            for (int cursor = gridStart; cursor + totalMinutesNeeded <= range.end(); cursor += granularity) {
                slots.add(ZonedDateTime.of(date, toLocalTime(cursor), providerZone).toInstant());
            }
        }

        slots.sort(Instant::compareTo);
        return new AvailabilityResponse(date, slots);
    }

    private void validateDateWindow(LocalDate date) {
        ZoneId clinicZone = ZoneId.of(bookingProperties.getClinicTimezone());
        LocalDate today = LocalDate.now(clinicZone);
        if (date.isBefore(today)) {
            throw new InvalidAppointmentDateException();
        }
        if (date.isAfter(today.plusDays(bookingProperties.getMaxBookingWindowDays()))) {
            throw new BookingWindowExceededException();
        }
    }

    /**
     * Clips each overlapping range — provider_unavailability (§7.5/§12.2/§12.3) or
     * an active slot_hold (§7.8/§12.10) — to the requested calendar date's
     * boundaries in the provider's timezone, and expresses the clipped portion as
     * a minute-of-day range so it can be subtracted alongside BREAK rules.
     */
    private List<MinuteRange> clipToDate(List<TimeRange> ranges, Instant dayStart, Instant dayEnd, ZoneId providerZone) {
        List<MinuteRange> result = new ArrayList<>();
        for (TimeRange range : ranges) {
            Instant clippedStart = range.start().isBefore(dayStart) ? dayStart : range.start();
            Instant clippedEnd = range.end().isAfter(dayEnd) ? dayEnd : range.end();

            int startMinutes = clippedStart.equals(dayStart)
                    ? 0 : toMinutes(ZonedDateTime.ofInstant(clippedStart, providerZone).toLocalTime());
            int endMinutes = clippedEnd.equals(dayEnd)
                    ? MINUTES_PER_DAY : toMinutes(ZonedDateTime.ofInstant(clippedEnd, providerZone).toLocalTime());

            result.add(new MinuteRange(startMinutes, endMinutes));
        }
        return result;
    }

    /** A half-open [start, end) range of instants — the common shape of a provider_unavailability row or a slot_hold. */
    private record TimeRange(Instant start, Instant end) {
    }

    private static int toMinutes(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    private static LocalTime toLocalTime(int minutesSinceMidnight) {
        return LocalTime.of(minutesSinceMidnight / 60, minutesSinceMidnight % 60);
    }

    private static int ceilToGrid(int minutesSinceMidnight, int granularity) {
        return ((minutesSinceMidnight + granularity - 1) / granularity) * granularity;
    }

    private static List<MinuteRange> subtractFromAll(List<MinuteRange> ranges, MinuteRange cut) {
        List<MinuteRange> result = new ArrayList<>();
        for (MinuteRange range : ranges) {
            result.addAll(range.subtract(cut));
        }
        return result;
    }

    /**
     * A half-open [start, end) range expressed in minutes since midnight (0-1440).
     * Kept as plain integers — rather than {@link LocalTime} arithmetic, which wraps
     * at midnight — so subtracting a range that touches the end of the day can never
     * silently wrap around to a bogus small value.
     */
    private record MinuteRange(int start, int end) {

        List<MinuteRange> subtract(MinuteRange cut) {
            if (cut.start() >= end || start >= cut.end()) {
                return List.of(this); // no overlap
            }
            List<MinuteRange> pieces = new ArrayList<>();
            if (start < cut.start()) {
                pieces.add(new MinuteRange(start, cut.start()));
            }
            if (cut.end() < end) {
                pieces.add(new MinuteRange(cut.end(), end));
            }
            return pieces;
        }
    }
}
