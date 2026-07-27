package com.clinic.booking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Single home for every numeric constant named in the PRD (§10) — e.g.
 * MIN_LEAD_TIME_HOURS, MAX_BOOKING_WINDOW_DAYS, HOLD_DURATION_MINUTES.
 * Populated incrementally as the milestones that own each rule are implemented;
 * intentionally empty until then so no constant is hardcoded elsewhere in the meantime.
 */
@ConfigurationProperties(prefix = "booking")
public class BookingProperties {
}
