import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { AsyncStateWrapperComponent } from '../shared/components/async-state-wrapper/async-state-wrapper.component';
import { AppHttpError } from '../core/interceptors/http-error.interceptor';
import { formatClinicDate, formatClinicTime } from '../core/clinic-info.const';
import { AppointmentDetailResponse } from '../booking/models/appointment.model';
import { BookingApiService } from '../booking/services/booking-api.service';
import { RescheduleActionComponent } from './reschedule-action.component';
import { TranslatePipe } from '../core/i18n/translate.pipe';

/**
 * `/appointments/:token` (PRD §3/§4/§8.7/§8.8/§8.19) — never gated for
 * view/cancel; reschedule is gated (§6) inside {@link RescheduleActionComponent}
 * itself via the backend. Reached from the link in the confirmation email.
 */
@Component({
  selector: 'app-appointment-lookup-page',
  standalone: true,
  imports: [AsyncStateWrapperComponent, RescheduleActionComponent, TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './appointment-lookup.page.html',
})
export class AppointmentLookupPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly bookingApi = inject(BookingApiService);

  private token = '';

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly appointment = signal<AppointmentDetailResponse | null>(null);

  readonly cancelReason = signal('');
  readonly cancelling = signal(false);
  readonly cancelError = signal<string | null>(null);

  ngOnInit(): void {
    this.token = this.route.snapshot.paramMap.get('token') ?? '';
    this.load();
  }

  formatDate(iso: string): string {
    return formatClinicDate(iso);
  }

  formatTime(iso: string): string {
    return formatClinicTime(iso);
  }

  cancel(): void {
    this.cancelling.set(true);
    this.cancelError.set(null);
    this.bookingApi.cancelAppointment(this.token, this.cancelReason() || undefined).subscribe({
      next: (updated) => {
        this.cancelling.set(false);
        this.appointment.set(updated);
      },
      error: (err: AppHttpError) => {
        this.cancelling.set(false);
        this.cancelError.set(err.userMessage);
      },
    });
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.bookingApi.getAppointment(this.token).subscribe({
      next: (appointment) => {
        this.loading.set(false);
        this.appointment.set(appointment);
      },
      error: (err: AppHttpError) => {
        this.loading.set(false);
        this.error.set(err.userMessage);
      },
    });
  }
}
