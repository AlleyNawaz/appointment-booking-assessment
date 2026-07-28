import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';

import { AsyncStateWrapperComponent } from '../../shared/components/async-state-wrapper/async-state-wrapper.component';
import { AppHttpError } from '../../core/interceptors/http-error.interceptor';
import { StaffSessionService } from '../auth/staff-session.service';
import { AvailabilityApiService } from './availability-api.service';
import { Unavailability } from './availability.model';
import { StaffNavComponent } from '../staff-nav.component';

/** `/staff/availability/unavailability` (PRD §4/§8.15) — vacation/sick-leave/emergency-closure blocks. */
@Component({
  selector: 'app-unavailability-page',
  standalone: true,
  imports: [AsyncStateWrapperComponent, StaffNavComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './unavailability.page.html',
})
export class UnavailabilityPage implements OnInit {
  private readonly availabilityApi = inject(AvailabilityApiService);
  private readonly staffSession = inject(StaffSessionService);

  /** §4.1 nav matrix: Availability → Time Off is read-only (👁) for ROLE_SYSADMIN. */
  readonly canWrite = computed(() => {
    const role = this.staffSession.role();
    return role === 'ROLE_STAFF' || role === 'ROLE_ADMIN' || role === 'ROLE_PROVIDER';
  });
  readonly isProvider = computed(() => this.staffSession.role() === 'ROLE_PROVIDER');

  readonly providerIdInput = signal(this.staffSession.providerId()?.toString() ?? '');
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly entries = signal<Unavailability[] | null>(null);

  readonly startDatetime = signal('');
  readonly endDatetime = signal('');
  readonly reason = signal('');
  readonly formError = signal<string | null>(null);
  readonly lastAffectedAppointments = signal<Unavailability['affectedAppointments'] | null>(null);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    const providerId = this.effectiveProviderId();
    if (providerId == null) {
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.availabilityApi.listUnavailability(providerId).subscribe({
      next: (entries) => {
        this.loading.set(false);
        this.entries.set(entries);
      },
      error: (err: AppHttpError) => {
        this.loading.set(false);
        this.error.set(err.userMessage);
      },
    });
  }

  onProviderIdChange(value: string): void {
    this.providerIdInput.set(value);
    this.load();
  }

  create(): void {
    const providerId = this.effectiveProviderId();
    if (providerId == null) {
      return;
    }
    this.formError.set(null);
    this.availabilityApi
      .createUnavailability(providerId, {
        startDatetime: this.startDatetime(),
        endDatetime: this.endDatetime(),
        reason: this.reason(),
      })
      .subscribe({
        next: (created) => {
          this.lastAffectedAppointments.set(created.affectedAppointments);
          this.reason.set('');
          this.load();
        },
        error: (err: AppHttpError) => this.formError.set(err.userMessage),
      });
  }

  delete(id: number): void {
    this.availabilityApi.deleteUnavailability(id).subscribe({
      next: () => this.load(),
      error: (err: AppHttpError) => this.error.set(err.userMessage),
    });
  }

  private effectiveProviderId(): number | null {
    if (this.isProvider()) {
      return this.staffSession.providerId();
    }
    return this.providerIdInput() ? Number(this.providerIdInput()) : null;
  }
}
