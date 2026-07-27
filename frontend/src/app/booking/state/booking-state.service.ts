import { Injectable, computed, signal } from '@angular/core';

import { AppointmentType } from '../models/appointment-type.model';
import { Provider } from '../models/provider.model';
import { SlotHold } from '../models/slot-hold.model';

export interface ContactDetails {
  fullName: string;
  email: string;
  phone: string;
  notes: string;
}

interface PersistedBookingState {
  appointmentType: AppointmentType | null;
  provider: Provider | null;
  selectedDate: string | null;
  selectedSlot: string | null;
  hold: SlotHold | null;
  contact: ContactDetails | null;
  idempotencyKey: string | null;
}

const STORAGE_KEY = 'booking-wizard-state';

/**
 * Signal-based state for the linear booking wizard (PRD §9) — no NgRx: this
 * is a single, linear five-step flow with no state shared outside it. The
 * hold token and in-progress form data live here and are mirrored to
 * `sessionStorage` (not component state) so a browser refresh at any step
 * restores the in-progress booking as long as the hold hasn't expired
 * (§3/§19 #5). An expired hold clears itself but retains the typed contact
 * info, matching the hold-timeout alternate flow in §3.
 */
@Injectable({ providedIn: 'root' })
export class BookingStateService {
  private readonly _appointmentType = signal<AppointmentType | null>(null);
  private readonly _provider = signal<Provider | null>(null);
  private readonly _selectedDate = signal<string | null>(null);
  private readonly _selectedSlot = signal<string | null>(null);
  private readonly _hold = signal<SlotHold | null>(null);
  private readonly _contact = signal<ContactDetails | null>(null);
  private readonly _flashMessage = signal<string | null>(null);
  private idempotencyKey: string | null = null;

  readonly appointmentType = this._appointmentType.asReadonly();
  readonly provider = this._provider.asReadonly();
  readonly selectedDate = this._selectedDate.asReadonly();
  readonly selectedSlot = this._selectedSlot.asReadonly();
  readonly hold = this._hold.asReadonly();
  readonly contact = this._contact.asReadonly();
  readonly flashMessage = this._flashMessage.asReadonly();

  readonly hasValidHold = computed(() => {
    const hold = this._hold();
    return !!hold && new Date(hold.expiresAt).getTime() > Date.now();
  });

  constructor() {
    this.restore();
  }

  setAppointmentType(appointmentType: AppointmentType): void {
    this._appointmentType.set(appointmentType);
    this._provider.set(null);
    this._selectedDate.set(null);
    this._selectedSlot.set(null);
    this._hold.set(null);
    this.persist();
  }

  setProvider(provider: Provider): void {
    this._provider.set(provider);
    this._selectedDate.set(null);
    this._selectedSlot.set(null);
    this._hold.set(null);
    this.persist();
  }

  setSelectedDate(date: string): void {
    this._selectedDate.set(date);
    this._selectedSlot.set(null);
    this._hold.set(null);
    this.persist();
  }

  setSelectedSlot(slot: string): void {
    this._selectedSlot.set(slot);
    this.persist();
  }

  setHold(hold: SlotHold): void {
    this._hold.set(hold);
    this.persist();
  }

  /**
   * §3: a slot hold expiring mid-flow returns the patient to slot selection
   * but keeps their contact info. {@code message} is the exact §3 copy,
   * read once by the schedule-selection page and cleared immediately after.
   */
  clearExpiredHold(message: string): void {
    this._hold.set(null);
    this._selectedSlot.set(null);
    this._flashMessage.set(message);
    this.persist();
  }

  consumeFlashMessage(): string | null {
    const message = this._flashMessage();
    this._flashMessage.set(null);
    return message;
  }

  setContact(contact: ContactDetails): void {
    this._contact.set(contact);
    this.persist();
  }

  /** §8.6: the same key must be reused across retries of the same submission attempt. */
  getOrCreateIdempotencyKey(): string {
    if (!this.idempotencyKey) {
      this.idempotencyKey = crypto.randomUUID();
    }
    return this.idempotencyKey;
  }

  /** Called once a booking has been created (or the wizard is abandoned) so a fresh visit starts clean. */
  reset(): void {
    this._appointmentType.set(null);
    this._provider.set(null);
    this._selectedDate.set(null);
    this._selectedSlot.set(null);
    this._hold.set(null);
    this._contact.set(null);
    this.idempotencyKey = null;
    sessionStorage.removeItem(STORAGE_KEY);
  }

  private persist(): void {
    const state: PersistedBookingState = {
      appointmentType: this._appointmentType(),
      provider: this._provider(),
      selectedDate: this._selectedDate(),
      selectedSlot: this._selectedSlot(),
      hold: this._hold(),
      contact: this._contact(),
      idempotencyKey: this.idempotencyKey,
    };
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  }

  private restore(): void {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return;
    }
    try {
      const state: PersistedBookingState = JSON.parse(raw);
      this._appointmentType.set(state.appointmentType);
      this._provider.set(state.provider);
      this._selectedDate.set(state.selectedDate);
      this._contact.set(state.contact);
      this.idempotencyKey = state.idempotencyKey;

      const holdStillValid = !!state.hold && new Date(state.hold.expiresAt).getTime() > Date.now();
      if (holdStillValid) {
        this._hold.set(state.hold);
        this._selectedSlot.set(state.selectedSlot);
      }
    } catch {
      sessionStorage.removeItem(STORAGE_KEY);
    }
  }
}
