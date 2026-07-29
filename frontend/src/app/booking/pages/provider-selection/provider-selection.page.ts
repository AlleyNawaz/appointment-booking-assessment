import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';

import { AsyncStateWrapperComponent } from '../../../shared/components/async-state-wrapper/async-state-wrapper.component';
import { AppHeaderComponent } from '../../../shared/layout/app-header/app-header.component';
import { BookingStepperComponent } from '../../../shared/layout/booking-stepper/booking-stepper.component';
import { BookingSidebarComponent } from '../../../shared/layout/booking-sidebar/booking-sidebar.component';
import { AppHttpError } from '../../../core/interceptors/http-error.interceptor';
import { Provider } from '../../models/provider.model';
import { BookingApiService } from '../../services/booking-api.service';
import { BookingStateService } from '../../state/booking-state.service';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';

/** `/book/provider` (PRD §4/§8.3) — providers offering the previously selected type. */
@Component({
  selector: 'app-provider-selection-page',
  standalone: true,
  imports: [
    AsyncStateWrapperComponent,
    AppHeaderComponent,
    BookingStepperComponent,
    BookingSidebarComponent,
    MatIconModule,
    TranslatePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './provider-selection.page.html',
  styleUrl: './provider-selection.page.scss',
})
export class ProviderSelectionPage implements OnInit {
  private readonly bookingApi = inject(BookingApiService);
  private readonly bookingState = inject(BookingStateService);
  private readonly router = inject(Router);

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly providers = signal<Provider[]>([]);
  readonly selectedProviderId = signal<number | null>(null);

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

  /** Presentational only — avatar initials. */
  initialsFor(provider: Provider): string {
    return `${provider.firstName.charAt(0)}${provider.lastName.charAt(0)}`.toUpperCase();
  }

  select(provider: Provider): void {
    this.selectedProviderId.set(provider.id);
    this.bookingState.setProvider(provider);
    this.router.navigateByUrl('/book/schedule');
  }
}
