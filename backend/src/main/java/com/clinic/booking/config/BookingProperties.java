package com.clinic.booking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Single home for every numeric/config constant named in the PRD (§10) — e.g.
 * MIN_LEAD_TIME_HOURS, MAX_BOOKING_WINDOW_DAYS, HOLD_DURATION_MINUTES.
 * Populated incrementally as the milestones that own each rule are implemented,
 * so no constant is ever hardcoded inline in a service method (§10).
 */
@ConfigurationProperties(prefix = "booking")
public class BookingProperties {

    /** §11.10: the clinic's single configured IANA timezone, used for date-window validation (§8.4). */
    private String clinicTimezone = "America/New_York";

    /** §8.4/§11.3: MAX_BOOKING_WINDOW_DAYS — how many days out a date may be requested/booked. */
    private int maxBookingWindowDays = 90;

    /** §8.4: SLOT_GRANULARITY_MINUTES — the grid granularity for computed slot start times. */
    private int slotGranularityMinutes = 15;

    public String getClinicTimezone() {
        return clinicTimezone;
    }

    public void setClinicTimezone(String clinicTimezone) {
        this.clinicTimezone = clinicTimezone;
    }

    public int getMaxBookingWindowDays() {
        return maxBookingWindowDays;
    }

    public void setMaxBookingWindowDays(int maxBookingWindowDays) {
        this.maxBookingWindowDays = maxBookingWindowDays;
    }

    public int getSlotGranularityMinutes() {
        return slotGranularityMinutes;
    }

    public void setSlotGranularityMinutes(int slotGranularityMinutes) {
        this.slotGranularityMinutes = slotGranularityMinutes;
    }
}
