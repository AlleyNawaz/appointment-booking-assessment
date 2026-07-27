/** GET /booking/availability response (PRD §8.4). */
export interface AvailabilityResponse {
  date: string;
  slots: string[];
}

/** POST /booking/holds response (PRD §8.5) — a 5-minute soft lock on one slot. */
export interface SlotHold {
  holdToken: string;
  expiresAt: string;
}
