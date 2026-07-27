import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { AppHttpError } from '../../../core/interceptors/http-error.interceptor';
import { formatClinicDate, formatClinicTime } from '../../../core/clinic-info.const';
import { AppointmentResponse } from '../../models/appointment.model';
import { BookingApiService } from '../../services/booking-api.service';
import { BookingStateService, ContactDetails } from '../../state/booking-state.service';

/** `/book/confirm` (PRD §3/§4/§8.6) — final review, submit, and the success/pending result. */
@Component({
  selector: 'app-review-confirm-page',
  standalone: true,
  imports: [],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './review-confirm.page.html',
  styleUrl: './review-confirm.page.scss',
})
export class ReviewConfirmPage implements OnInit {
  private readonly bookingApi = inject(BookingApiService);
  private readonly bookingState = inject(BookingStateService);
  private readonly router = inject(Router);

  readonly providerName = signal('');
  readonly appointmentTypeName = signal('');
  readonly slotDisplay = signal('');
  readonly contact = signal<ContactDetails | null>(null);

  readonly submitting = signal(false);
  readonly submitError = signal<string | null>(null);
  readonly result = signal<AppointmentResponse | null>(null);

  ngOnInit(): void {
    if (!this.bookingState.hasValidHold() || !this.bookingState.contact()) {
      this.router.navigateByUrl('/book/schedule');
      return;
    }

    const provider = this.bookingState.provider();
    const appointmentType = this.bookingState.appointmentType();
    const slot = this.bookingState.selectedSlot();
    this.providerName.set(provider ? `Dr. ${provider.firstName} ${provider.lastName}` : '');
    this.appointmentTypeName.set(appointmentType?.displayName ?? '');
    this.slotDisplay.set(slot ? `${formatClinicDate(slot)} at ${formatClinicTime(slot)}` : '');
    this.contact.set(this.bookingState.contact());
  }

  submit(): void {
    const hold = this.bookingState.hold();
    const contact = this.bookingState.contact();
    if (!hold || !contact) {
      return;
    }

    this.submitting.set(true);
    this.submitError.set(null);
    this.bookingApi
      .createAppointment(
        {
          holdToken: hold.holdToken,
          patientFullName: contact.fullName,
          patientEmail: contact.email,
          patientPhone: contact.phone,
          notes: contact.notes || undefined,
        },
        this.bookingState.getOrCreateIdempotencyKey()
      )
      .subscribe({
        next: (response) => {
          this.submitting.set(false);
          this.result.set(response);
          this.bookingState.reset();
        },
        error: (err: AppHttpError) => {
          this.submitting.set(false);
          if (err.errorCode === 'SLOT_HOLD_EXPIRED') {
            // §3: hold-timeout mid-flow returns to slot selection; contact info is retained.
            this.bookingState.clearExpiredHold(
              'That time slot was only held for 5 minutes and has been released — please pick a time again.'
            );
            this.router.navigateByUrl('/book/schedule');
            return;
          }
          this.submitError.set(err.userMessage);
        },
      });
  }
}
