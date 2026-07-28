import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';

import { AsyncStateWrapperComponent } from '../../shared/components/async-state-wrapper/async-state-wrapper.component';
import { AppHttpError } from '../../core/interceptors/http-error.interceptor';
import { StaffSessionService } from '../auth/staff-session.service';
import { AvailabilityApiService } from './availability-api.service';
import { AvailabilityRule, RuleType } from './availability.model';
import { StaffNavComponent } from '../staff-nav.component';

/** `/staff/availability/hours` (PRD §4/§8.14) — provider working-hours/break rules. */
@Component({
  selector: 'app-hours-page',
  standalone: true,
  imports: [AsyncStateWrapperComponent, StaffNavComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './hours.page.html',
})
export class HoursPage implements OnInit {
  private readonly availabilityApi = inject(AvailabilityApiService);
  private readonly staffSession = inject(StaffSessionService);

  /** §4.1 nav matrix: Availability → Hours write access is ROLE_ADMIN only. */
  readonly canWrite = computed(() => this.staffSession.role() === 'ROLE_ADMIN');
  readonly isProvider = computed(() => this.staffSession.role() === 'ROLE_PROVIDER');

  readonly providerIdInput = signal(this.staffSession.providerId()?.toString() ?? '');
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly rules = signal<AvailabilityRule[] | null>(null);

  readonly dayOfWeek = signal(1);
  readonly startTime = signal('09:00:00');
  readonly endTime = signal('17:00:00');
  readonly ruleType = signal<RuleType>('WORKING');
  readonly formError = signal<string | null>(null);

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
    this.availabilityApi.listRules(providerId).subscribe({
      next: (rules) => {
        this.loading.set(false);
        this.rules.set(rules);
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

  addRule(): void {
    const providerId = this.effectiveProviderId();
    if (providerId == null) {
      return;
    }
    this.formError.set(null);
    this.availabilityApi
      .createRule(providerId, {
        dayOfWeek: this.dayOfWeek(),
        startTime: this.startTime(),
        endTime: this.endTime(),
        ruleType: this.ruleType(),
      })
      .subscribe({
        next: () => this.load(),
        error: (err: AppHttpError) => this.formError.set(err.userMessage),
      });
  }

  deleteRule(id: number): void {
    this.availabilityApi.deleteRule(id).subscribe({
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
