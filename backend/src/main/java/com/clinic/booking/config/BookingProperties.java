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
    private String clinicTimezone = "Asia/Kuala_Lumpur";

    /** §8.4/§11.3: MAX_BOOKING_WINDOW_DAYS — how many days out a date may be requested/booked. */
    private int maxBookingWindowDays = 90;

    /** §8.4: SLOT_GRANULARITY_MINUTES — the grid granularity for computed slot start times. */
    private int slotGranularityMinutes = 15;

    /** §8.5/§12.10/§12.11: HOLD_DURATION_MINUTES — how long a slot hold reserves a slot before it expires. */
    private int holdDurationMinutes = 5;

    /** §11.2: MIN_LEAD_TIME_HOURS — minimum hours between now and a bookable startDateTime. */
    private int minLeadTimeHours = 24;

    /** §8.8/§12.6: CANCELLATION_CUTOFF_HOURS — how close to startDateTime self-service cancellation is still allowed. */
    private int cancellationCutoffHours = 4;

    /** §8.8/§12.6: clinic phone number surfaced when self-service cancellation is no longer available. */
    private String clinicPhoneNumber = "+1-555-0100";

    /** §15.9: BCRYPT_STRENGTH — BCrypt cost factor for staff password hashing. */
    private int bcryptStrength = 12;

    /** §15.9: minimum staff password length. */
    private int minPasswordLength = 12;

    /** §15.9: maximum staff password length (BCrypt silently truncates beyond 72 bytes). */
    private int maxPasswordLength = 72;

    /** §15.9: MAX_FAILED_LOGIN_ATTEMPTS — consecutive failures before lockout. */
    private int maxFailedLoginAttempts = 5;

    /** §15.9: LOGIN_LOCKOUT_MINUTES — how long an account stays locked once tripped. */
    private int loginLockoutMinutes = 15;

    /** §15.9: SESSION_IDLE_TIMEOUT_MINUTES — staff session idle expiry. */
    private int sessionIdleTimeoutMinutes = 30;

    /** §15.9: SESSION_ABSOLUTE_TIMEOUT_HOURS — staff session absolute expiry regardless of activity. */
    private int sessionAbsoluteTimeoutHours = 8;

    /** §12.7/§12.11: APPROVAL_TIMEOUT_HOURS — a PENDING appointment with no staff action auto-expires after this. */
    private int approvalTimeoutHours = 24;

    /** §12.7: how long past end_datetime a still-CONFIRMED appointment is marked MISSED by the nightly job. */
    private int missedMarkerGraceHours = 24;

    /** §12.7/§19 #26: staff may correct a MISSED appointment to COMPLETED within this many days of end_datetime. */
    private int missedCorrectionWindowDays = 7;

    /** §15.7: per-IP cap on GET /booking/availability requests per minute. */
    private int availabilityRateLimitPerMinute = 10;

    /** §15.7/§12.5: per-IP cap on POST /booking/holds + POST /booking/appointments combined, per 10-minute window. */
    private int bookingRateLimitPerTenMinutes = 5;

    /**
     * Optional BCC address for outbound patient notification emails (booking/cancellation/
     * reschedule) — e.g. a clinic-wide monitoring inbox. Blank (the default) disables BCC
     * entirely; this must never default to a real address, since patient contact details are
     * in the email body and §15.5/§15.6's sensitive-data-handling principle applies here too.
     */
    private String notificationBccEmail = "";

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

    public int getHoldDurationMinutes() {
        return holdDurationMinutes;
    }

    public void setHoldDurationMinutes(int holdDurationMinutes) {
        this.holdDurationMinutes = holdDurationMinutes;
    }

    public int getMinLeadTimeHours() {
        return minLeadTimeHours;
    }

    public void setMinLeadTimeHours(int minLeadTimeHours) {
        this.minLeadTimeHours = minLeadTimeHours;
    }

    public int getCancellationCutoffHours() {
        return cancellationCutoffHours;
    }

    public void setCancellationCutoffHours(int cancellationCutoffHours) {
        this.cancellationCutoffHours = cancellationCutoffHours;
    }

    public String getClinicPhoneNumber() {
        return clinicPhoneNumber;
    }

    public void setClinicPhoneNumber(String clinicPhoneNumber) {
        this.clinicPhoneNumber = clinicPhoneNumber;
    }

    public int getBcryptStrength() {
        return bcryptStrength;
    }

    public void setBcryptStrength(int bcryptStrength) {
        this.bcryptStrength = bcryptStrength;
    }

    public int getMinPasswordLength() {
        return minPasswordLength;
    }

    public void setMinPasswordLength(int minPasswordLength) {
        this.minPasswordLength = minPasswordLength;
    }

    public int getMaxPasswordLength() {
        return maxPasswordLength;
    }

    public void setMaxPasswordLength(int maxPasswordLength) {
        this.maxPasswordLength = maxPasswordLength;
    }

    public int getMaxFailedLoginAttempts() {
        return maxFailedLoginAttempts;
    }

    public void setMaxFailedLoginAttempts(int maxFailedLoginAttempts) {
        this.maxFailedLoginAttempts = maxFailedLoginAttempts;
    }

    public int getLoginLockoutMinutes() {
        return loginLockoutMinutes;
    }

    public void setLoginLockoutMinutes(int loginLockoutMinutes) {
        this.loginLockoutMinutes = loginLockoutMinutes;
    }

    public int getSessionIdleTimeoutMinutes() {
        return sessionIdleTimeoutMinutes;
    }

    public void setSessionIdleTimeoutMinutes(int sessionIdleTimeoutMinutes) {
        this.sessionIdleTimeoutMinutes = sessionIdleTimeoutMinutes;
    }

    public int getSessionAbsoluteTimeoutHours() {
        return sessionAbsoluteTimeoutHours;
    }

    public void setSessionAbsoluteTimeoutHours(int sessionAbsoluteTimeoutHours) {
        this.sessionAbsoluteTimeoutHours = sessionAbsoluteTimeoutHours;
    }

    public int getApprovalTimeoutHours() {
        return approvalTimeoutHours;
    }

    public void setApprovalTimeoutHours(int approvalTimeoutHours) {
        this.approvalTimeoutHours = approvalTimeoutHours;
    }

    public int getMissedMarkerGraceHours() {
        return missedMarkerGraceHours;
    }

    public void setMissedMarkerGraceHours(int missedMarkerGraceHours) {
        this.missedMarkerGraceHours = missedMarkerGraceHours;
    }

    public int getMissedCorrectionWindowDays() {
        return missedCorrectionWindowDays;
    }

    public void setMissedCorrectionWindowDays(int missedCorrectionWindowDays) {
        this.missedCorrectionWindowDays = missedCorrectionWindowDays;
    }

    public int getAvailabilityRateLimitPerMinute() {
        return availabilityRateLimitPerMinute;
    }

    public void setAvailabilityRateLimitPerMinute(int availabilityRateLimitPerMinute) {
        this.availabilityRateLimitPerMinute = availabilityRateLimitPerMinute;
    }

    public int getBookingRateLimitPerTenMinutes() {
        return bookingRateLimitPerTenMinutes;
    }

    public void setBookingRateLimitPerTenMinutes(int bookingRateLimitPerTenMinutes) {
        this.bookingRateLimitPerTenMinutes = bookingRateLimitPerTenMinutes;
    }

    public String getNotificationBccEmail() {
        return notificationBccEmail;
    }

    public void setNotificationBccEmail(String notificationBccEmail) {
        this.notificationBccEmail = notificationBccEmail;
    }
}
