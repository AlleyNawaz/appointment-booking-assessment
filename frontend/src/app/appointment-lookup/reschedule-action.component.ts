import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';

import { AppHttpError } from '../core/interceptors/http-error.interceptor';
import { formatClinicTime } from '../core/clinic-info.const';
import { AppointmentDetailResponse } from '../booking/models/appointment.model';
import { BookingApiService } from '../booking/services/booking-api.service';
import { CLINIC_TIMEZONE } from '../core/clinic-info.const';
import { TranslatePipe } from '../core/i18n/translate.pipe';

const MAX_BOOKING_WINDOW_DAYS = 90;

function todayInClinicTimezone(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: CLINIC_TIMEZONE }).format(new Date());
}

function addDays(isoDate: string, days: number): string {
  const [year, month, day] = isoDate.split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, day + days));
  return date.toISOString().slice(0, 10);
}

/**
 * Reschedule action for `/appointments/:token` (PRD §3/§8.19/§12.13) — lets the
 * patient pick a new date/time for the same provider and appointment type,
 * acquire a hold (reusing the exact same availability/hold infrastructure as
 * the booking wizard's schedule-selection step), then submit the atomic
 * reschedule. On success, navigates to the new confirmation token's page.
 */
@Component({
  selector: 'app-reschedule-action',
  standalone: true,
  imports: [TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './reschedule-action.component.html',
})
export class RescheduleActionComponent {
  private readonly bookingApi = inject(BookingApiService);
  private readonly router = inject(Router);

  readonly appointment = input.required<AppointmentDetailResponse>();
  readonly confirmationToken = input.required<string>();

  readonly minDate = todayInClinicTimezone();
  readonly maxDate = addDays(this.minDate, MAX_BOOKING_WINDOW_DAYS);

  readonly expanded = signal(false);
  readonly selectedDate = signal('');

  readonly slotsLoading = signal(false);
  readonly slotsError = signal<string | null>(null);
  readonly slots = signal<string[]>([]);

  readonly selectedSlot = signal<string | null>(null);
  readonly holdToken = signal<string | null>(null);
  readonly holdError = signal<string | null>(null);
  readonly holdSubmitting = signal(false);

  readonly reason = signal('');
  readonly rescheduling = signal(false);
  readonly rescheduleError = signal<string | null>(null);

  private idempotencyKey = crypto.randomUUID();

  formatSlotTime(slot: string): string {
    return formatClinicTime(slot);
  }

  start(): void {
    this.expanded.set(true);
  }

  cancel(): void {
    this.expanded.set(false);
    this.resetSelection();
  }

  onDateChange(date: string): void {
    this.selectedDate.set(date);
    this.selectedSlot.set(null);
    this.holdToken.set(null);
    this.holdError.set(null);
    this.loadSlots(date);
  }

  selectSlot(slot: string): void {
    this.holdError.set(null);
    this.holdSubmitting.set(true);
    this.bookingApi.createHold(this.appointment().providerId, this.appointment().appointmentTypeId, slot).subscribe({
      next: (hold) => {
        this.holdSubmitting.set(false);
        this.selectedSlot.set(slot);
        this.holdToken.set(hold.holdToken);
      },
      error: (err: AppHttpError) => {
        this.holdSubmitting.set(false);
        this.holdError.set(err.userMessage);
        this.loadSlots(this.selectedDate());
      },
    });
  }

  submit(): void {
    const holdToken = this.holdToken();
    if (!holdToken) {
      return;
    }
    this.rescheduling.set(true);
    this.rescheduleError.set(null);
    this.bookingApi
      .reschedule(
        this.confirmationToken(),
        { holdToken, reason: this.reason() || undefined },
        this.idempotencyKey
      )
      .subscribe({
        next: (response) => {
          this.rescheduling.set(false);
          this.router.navigateByUrl(`/appointments/${response.confirmationToken}`);
        },
        error: (err: AppHttpError) => {
          this.rescheduling.set(false);
          if (err.errorCode === 'SLOT_HOLD_EXPIRED') {
            this.selectedSlot.set(null);
            this.holdToken.set(null);
            this.idempotencyKey = crypto.randomUUID();
            // err.userMessage is already resolved via the existing error-messages.const.ts
            // mechanism — reused here rather than duplicating the string.
            this.rescheduleError.set(err.userMessage);
            this.loadSlots(this.selectedDate());
            return;
          }
          this.rescheduleError.set(err.userMessage);
        },
      });
  }

  private loadSlots(date: string): void {
    if (!date) {
      return;
    }
    this.slotsLoading.set(true);
    this.slotsError.set(null);
    this.slots.set([]);
    this.bookingApi.getAvailability(this.appointment().providerId, this.appointment().appointmentTypeId, date).subscribe({
      next: (response) => {
        this.slotsLoading.set(false);
        this.slots.set(response.slots);
      },
      error: (err: AppHttpError) => {
        this.slotsLoading.set(false);
        this.slotsError.set(err.userMessage);
      },
    });
  }

  private resetSelection(): void {
    this.selectedDate.set('');
    this.slots.set([]);
    this.slotsError.set(null);
    this.selectedSlot.set(null);
    this.holdToken.set(null);
    this.holdError.set(null);
    this.reason.set('');
    this.rescheduleError.set(null);
    this.idempotencyKey = crypto.randomUUID();
  }
}
