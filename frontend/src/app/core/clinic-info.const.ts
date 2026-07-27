/**
 * The patient-facing API never exposes the clinic's phone number or its IANA
 * timezone (provider timezone is deliberately excluded from `ProviderResponse`,
 * PRD §8.3) — these mirror the backend's own configured defaults
 * (`BookingProperties.clinicPhoneNumber`/`clinicTimezone`) so the two stay
 * consistent without changing a locked backend contract.
 */
export const CLINIC_PHONE_NUMBER = '+1-555-0100';
export const CLINIC_TIMEZONE = 'America/New_York';

/** §11.10: always render and explicitly label clinic-local time, e.g. "1:00 PM EDT" — never the visitor's browser timezone. */
export function formatClinicTime(isoInstant: string): string {
  return new Intl.DateTimeFormat('en-US', {
    timeZone: CLINIC_TIMEZONE,
    hour: 'numeric',
    minute: '2-digit',
    timeZoneName: 'short',
  }).format(new Date(isoInstant));
}

export function formatClinicDate(isoInstant: string): string {
  return new Intl.DateTimeFormat('en-US', {
    timeZone: CLINIC_TIMEZONE,
    weekday: 'long',
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  }).format(new Date(isoInstant));
}
