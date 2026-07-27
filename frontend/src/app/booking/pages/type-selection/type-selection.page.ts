import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { AsyncStateWrapperComponent } from '../../../shared/components/async-state-wrapper/async-state-wrapper.component';
import { AppointmentType } from '../../models/appointment-type.model';
import { BookingApiService } from '../../services/booking-api.service';
import { BookingStateService } from '../../state/booking-state.service';
import { AppHttpError } from '../../../core/interceptors/http-error.interceptor';

/** `/book/type` (PRD §4/§8.2) — list active appointment types. */
@Component({
  selector: 'app-type-selection-page',
  standalone: true,
  imports: [AsyncStateWrapperComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>Select Appointment Type</h1>
    <app-async-state-wrapper
      [loading]="loading()"
      [error]="error()"
      [empty]="!loading() && !error() && types().length === 0"
      emptyMessage="No appointment types are currently available."
    >
      <ul class="option-list">
        @for (type of types(); track type.id) {
          <li>
            <button type="button" (click)="select(type)">
              <span>{{ type.displayName }}</span>
              <span class="option-list__meta">{{ type.durationMinutes }} min</span>
            </button>
          </li>
        }
      </ul>
    </app-async-state-wrapper>
  `,
})
export class TypeSelectionPage implements OnInit {
  private readonly bookingApi = inject(BookingApiService);
  private readonly bookingState = inject(BookingStateService);
  private readonly router = inject(Router);

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly types = signal<AppointmentType[]>([]);

  ngOnInit(): void {
    this.bookingApi.getAppointmentTypes().subscribe({
      next: (types) => {
        this.loading.set(false);
        this.types.set(types);
      },
      error: (err: AppHttpError) => {
        this.loading.set(false);
        this.error.set(err.userMessage);
      },
    });
  }

  select(type: AppointmentType): void {
    this.bookingState.setAppointmentType(type);
    this.router.navigateByUrl('/book/provider');
  }
}
