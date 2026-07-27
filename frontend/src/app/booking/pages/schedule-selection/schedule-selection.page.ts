import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { AsyncStateWrapperComponent } from '../../../shared/components/async-state-wrapper/async-state-wrapper.component';
import { AppHttpError } from '../../../core/interceptors/http-error.interceptor';
import { CLINIC_TIMEZONE, formatClinicTime } from '../../../core/clinic-info.const';
import { BookingApiService } from '../../services/booking-api.service';
import { BookingStateService } from '../../state/booking-state.service';

const MAX_BOOKING_WINDOW_DAYS = 90;

interface CalendarDay {
  date: string; // yyyy-MM-dd
  dayOfMonth: number;
  disabled: boolean;
  disabledReason: string;
}

function todayInClinicTimezone(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: CLINIC_TIMEZONE }).format(new Date());
}

function addDays(isoDate: string, days: number): string {
  const [year, month, day] = isoDate.split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, day + days));
  return date.toISOString().slice(0, 10);
}

/** `/book/schedule` (PRD §3/§4/§8.4/§8.5) — calendar + slot grid, then acquires a 5-minute hold. */
@Component({
  selector: 'app-schedule-selection-page',
  standalone: true,
  imports: [AsyncStateWrapperComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './schedule-selection.page.html',
  styleUrl: './schedule-selection.page.scss',
})
export class ScheduleSelectionPage implements OnInit {
  private readonly bookingApi = inject(BookingApiService);
  private readonly bookingState = inject(BookingStateService);
  private readonly router = inject(Router);

  private readonly today = todayInClinicTimezone();
  private readonly maxDate = addDays(this.today, MAX_BOOKING_WINDOW_DAYS);

  readonly viewedMonth = signal(this.today.slice(0, 7)); // yyyy-MM
  readonly calendarWeeks = signal<CalendarDay[][]>([]);
  readonly selectedDate = signal<string | null>(null);

  readonly slotsLoading = signal(false);
  readonly slotsError = signal<string | null>(null);
  readonly slots = signal<string[]>([]);

  readonly holdError = signal<string | null>(null);
  readonly holdSubmitting = signal(false);
  readonly flashMessage = signal<string | null>(null);

  ngOnInit(): void {
    if (!this.bookingState.provider() || !this.bookingState.appointmentType()) {
      this.router.navigateByUrl('/book/provider');
      return;
    }
    this.flashMessage.set(this.bookingState.consumeFlashMessage());
    this.renderMonth();
  }

  formatSlotTime(slot: string): string {
    return formatClinicTime(slot);
  }

  canGoToPreviousMonth(): boolean {
    return this.viewedMonth() > this.today.slice(0, 7);
  }

  canGoToNextMonth(): boolean {
    return this.viewedMonth() < this.maxDate.slice(0, 7);
  }

  previousMonth(): void {
    if (!this.canGoToPreviousMonth()) {
      return;
    }
    this.shiftMonth(-1);
  }

  nextMonth(): void {
    if (!this.canGoToNextMonth()) {
      return;
    }
    this.shiftMonth(1);
  }

  selectDate(day: CalendarDay): void {
    if (day.disabled) {
      return;
    }
    this.selectedDate.set(day.date);
    this.holdError.set(null);
    this.loadSlots(day.date);
  }

  selectSlot(slot: string): void {
    const provider = this.bookingState.provider();
    const appointmentType = this.bookingState.appointmentType();
    if (!provider || !appointmentType) {
      return;
    }
    this.holdError.set(null);
    this.holdSubmitting.set(true);
    this.bookingApi.createHold(provider.id, appointmentType.id, slot).subscribe({
      next: (hold) => {
        this.holdSubmitting.set(false);
        this.bookingState.setSelectedSlot(slot);
        this.bookingState.setHold(hold);
        this.router.navigateByUrl('/book/details');
      },
      error: (err: AppHttpError) => {
        this.holdSubmitting.set(false);
        this.holdError.set(err.userMessage);
        const date = this.selectedDate();
        if (date) {
          this.loadSlots(date);
        }
      },
    });
  }

  private shiftMonth(delta: number): void {
    const [year, month] = this.viewedMonth().split('-').map(Number);
    const next = new Date(Date.UTC(year, month - 1 + delta, 1));
    this.viewedMonth.set(`${next.getUTCFullYear()}-${String(next.getUTCMonth() + 1).padStart(2, '0')}`);
    this.renderMonth();
  }

  private renderMonth(): void {
    const [year, month] = this.viewedMonth().split('-').map(Number);
    const firstOfMonth = new Date(Date.UTC(year, month - 1, 1));
    const startOffset = firstOfMonth.getUTCDay(); // 0=Sunday
    const daysInMonth = new Date(Date.UTC(year, month, 0)).getUTCDate();

    const cells: (CalendarDay | null)[] = [...Array(startOffset).fill(null)];
    for (let day = 1; day <= daysInMonth; day++) {
      const date = `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
      const disabled = date < this.today || date > this.maxDate;
      cells.push({
        date,
        dayOfMonth: day,
        disabled,
        disabledReason: disabled ? 'Outside the booking window' : '',
      });
    }
    while (cells.length % 7 !== 0) {
      cells.push(null);
    }

    const weeks: CalendarDay[][] = [];
    for (let i = 0; i < cells.length; i += 7) {
      weeks.push(cells.slice(i, i + 7) as CalendarDay[]);
    }
    this.calendarWeeks.set(weeks);
  }

  private loadSlots(date: string): void {
    const provider = this.bookingState.provider();
    const appointmentType = this.bookingState.appointmentType();
    if (!provider || !appointmentType) {
      return;
    }
    this.slotsLoading.set(true);
    this.slotsError.set(null);
    this.slots.set([]);
    this.bookingApi.getAvailability(provider.id, appointmentType.id, date).subscribe({
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
}
