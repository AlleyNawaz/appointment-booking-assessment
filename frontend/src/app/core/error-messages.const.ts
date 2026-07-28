/**
 * Maps every backend `errorCode` (PRD §13) to a user-facing message. The
 * single lookup table `HttpErrorInterceptor` (and any page that needs a
 * specific message, e.g. hold expiry) reads from — adding a new backend
 * error code means adding one line here, never touching a page (§9).
 */
export const ERROR_MESSAGES: Record<string, string> = {
  FEATURE_DISABLED: 'Online booking is currently unavailable.',
  VALIDATION_ERROR: 'Please check the highlighted fields and try again.',
  INVALID_APPOINTMENT_DATE: 'The selected date has already passed.',
  LEAD_TIME_VIOLATION: 'This appointment must be booked further in advance.',
  BOOKING_WINDOW_EXCEEDED: 'The selected date is too far in the future.',
  CLINIC_CLOSED_DAY: 'The clinic or provider is not open on the selected date.',
  PROVIDER_UNAVAILABLE: 'This provider is no longer available. Please choose another.',
  SLOT_ALREADY_BOOKED: 'This time slot is no longer available.',
  SLOT_HOLD_EXPIRED:
    'That time slot was only held for 5 minutes and has been released — please pick a time again.',
  DUPLICATE_APPOINTMENT: 'You already have an appointment with this provider at this time.',
  PATIENT_DAILY_LIMIT_EXCEEDED: 'You have reached the maximum number of appointments allowed per day.',
  IDEMPOTENCY_KEY_REUSED_MISMATCH: 'This request could not be resubmitted. Please start again.',
  APPOINTMENT_NOT_FOUND: 'No appointment was found for the given confirmation link.',
  CANCELLATION_WINDOW_EXPIRED: 'This appointment can no longer be cancelled online.',
  INVALID_CREDENTIALS: 'Incorrect username or password.',
  ACCOUNT_LOCKED: 'This account is temporarily locked due to repeated failed login attempts. Please try again later.',
  STALE_VERSION: 'This appointment was updated elsewhere. Refresh and try again.',
  APPOINTMENT_NOT_RESCHEDULABLE: 'This appointment cannot be rescheduled in its current status.',
  APPOINTMENT_STATE_CHANGED:
    'This appointment was modified by clinic staff and can no longer be rescheduled. Refresh and try again.',
  RATE_LIMITED: 'Too many requests. Please wait a moment and try again.',
};

export const DEFAULT_ERROR_MESSAGE = 'Something went wrong. Please try again.';

export function errorMessageFor(errorCode: string | undefined | null): string {
  if (!errorCode) {
    return DEFAULT_ERROR_MESSAGE;
  }
  return ERROR_MESSAGES[errorCode] ?? DEFAULT_ERROR_MESSAGE;
}
