import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { AsyncStateWrapperComponent } from '../../../shared/components/async-state-wrapper/async-state-wrapper.component';
import { AppHttpError } from '../../../core/interceptors/http-error.interceptor';
import { Provider } from '../../models/provider.model';
import { BookingApiService } from '../../services/booking-api.service';
import { BookingStateService } from '../../state/booking-state.service';

/** `/book/provider` (PRD §4/§8.3) — providers offering the previously selected type. */
@Component({
  selector: 'app-provider-selection-page',
  standalone: true,
  imports: [AsyncStateWrapperComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>Select a Provider</h1>
    <app-async-state-wrapper
      [loading]="loading()"
      [error]="error()"
      [empty]="!loading() && !error() && providers().length === 0"
      emptyMessage="No providers currently offer this appointment type."
    >
      <ul class="option-list">
        @for (provider of providers(); track provider.id) {
          <li>
            <button type="button" (click)="select(provider)">
              <span>Dr. {{ provider.firstName }} {{ provider.lastName }}</span>
              <span class="option-list__meta">{{ provider.specialty }}</span>
            </button>
          </li>
        }
      </ul>
    </app-async-state-wrapper>
  `,
})
export class ProviderSelectionPage implements OnInit {
  private readonly bookingApi = inject(BookingApiService);
  private readonly bookingState = inject(BookingStateService);
  private readonly router = inject(Router);

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly providers = signal<Provider[]>([]);

  ngOnInit(): void {
    const appointmentType = this.bookingState.appointmentType();
    if (!appointmentType) {
      this.router.navigateByUrl('/book/type');
      return;
    }

    this.bookingApi.getProviders(appointmentType.id).subscribe({
      next: (providers) => {
        this.loading.set(false);
        this.providers.set(providers);
      },
      error: (err: AppHttpError) => {
        this.loading.set(false);
        this.error.set(err.userMessage);
      },
    });
  }

  select(provider: Provider): void {
    this.bookingState.setProvider(provider);
    this.router.navigateByUrl('/book/schedule');
  }
}
