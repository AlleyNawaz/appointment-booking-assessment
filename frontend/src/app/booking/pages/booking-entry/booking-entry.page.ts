import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { AsyncStateWrapperComponent } from '../../../shared/components/async-state-wrapper/async-state-wrapper.component';
import { CLINIC_PHONE_NUMBER } from '../../../core/clinic-info.const';
import { BookingApiService } from '../../services/booking-api.service';

/**
 * `/book` (PRD §4) — the flag check and entry point. Unlike the wizard-step
 * routes, this one is never guarded: it renders the "unavailable" state
 * itself (§6) rather than being redirected to, so a stale bookmark to a
 * disabled flag can never dead-end.
 */
@Component({
  selector: 'app-booking-entry-page',
  standalone: true,
  imports: [AsyncStateWrapperComponent, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-async-state-wrapper [loading]="loading()" [error]="error()">
      @if (!loading() && !error()) {
        @if (enabled()) {
          <p>Redirecting to appointment booking…</p>
        } @else {
          <div class="unavailable">
            <h1>Online booking is currently unavailable</h1>
            <p>Please call the clinic at <strong>{{ clinicPhoneNumber }}</strong> to schedule an appointment.</p>
          </div>
        }
      }
      <p><a routerLink="/appointments">Manage an existing appointment</a></p>
    </app-async-state-wrapper>
  `,
})
export class BookingEntryPage implements OnInit {
  private readonly bookingApi = inject(BookingApiService);
  private readonly router = inject(Router);

  readonly clinicPhoneNumber = CLINIC_PHONE_NUMBER;
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly enabled = signal(false);

  ngOnInit(): void {
    this.bookingApi.getConfig().subscribe({
      next: (config) => {
        this.loading.set(false);
        this.enabled.set(config.enabled);
        if (config.enabled) {
          this.router.navigateByUrl('/book/type');
        }
      },
      error: () => {
        this.loading.set(false);
        this.enabled.set(false);
      },
    });
  }
}
