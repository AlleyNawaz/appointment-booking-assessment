import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { AsyncStateWrapperComponent } from '../../../shared/components/async-state-wrapper/async-state-wrapper.component';
import { AppHeaderComponent } from '../../../shared/layout/app-header/app-header.component';
import { BookingStepperComponent } from '../../../shared/layout/booking-stepper/booking-stepper.component';
import { BookingSidebarComponent } from '../../../shared/layout/booking-sidebar/booking-sidebar.component';
import { AppHttpError } from '../../../core/interceptors/http-error.interceptor';
import { CLINIC_TIMEZONE, formatClinicTime } from '../../../core/clinic-info.const';
import { BookingApiService } from '../../services/booking-api.service';
import { BookingStateService } from '../../state/booking-state.service';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';

import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';

const MAX_BOOKING_WINDOW_DAYS = 90;

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
  imports: [
    AsyncStateWrapperComponent,
    AppHeaderComponent,
    BookingStepperComponent,
    BookingSidebarComponent,
    TranslatePipe,
    MatDatepickerModule,
    MatNativeDateModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './schedule-selection.page.html',
  styleUrl: './schedule-selection.page.scss',
})
export class ScheduleSelectionPage implements OnInit {
  private readonly bookingApi = inject(BookingApiService);
  private readonly bookingState = inject(BookingStateService);
  private readonly router = inject(Router);

  private readonly todayIso = todayInClinicTimezone();
  private readonly maxDateIso = addDays(this.todayIso, MAX_BOOKING_WINDOW_DAYS);

  readonly minDate = new Date(this.todayIso);
  readonly maxDate = new Date(this.maxDateIso);

  readonly selectedDateObj = signal<Date | null>(null);
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
  }

  formatSlotTime(slot: string): string {
    return formatClinicTime(slot);
  }

  selectDate(date: Date | null): void {
    this.selectedDateObj.set(date);
    if (!date) {
      this.selectedDate.set(null);
      return;
    }

    // Format date as yyyy-MM-dd
    const formattedDate = date.toLocaleDateString('en-CA'); // en-CA gives YYYY-MM-DD
    this.selectedDate.set(formattedDate);
    this.holdError.set(null);
    this.loadSlots(formattedDate);
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
