import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';

import { AsyncStateWrapperComponent } from '../../shared/components/async-state-wrapper/async-state-wrapper.component';
import { AppHttpError } from '../../core/interceptors/http-error.interceptor';
import { StaffNavComponent } from '../staff-nav.component';
import { AuditLogApiService } from './audit-log-api.service';
import { AuditLogPageResponse } from './audit-log.model';

/** `/staff/audit-log` (PRD §4/§8.18) — read-only, ROLE_SYSADMIN only. */
@Component({
  selector: 'app-audit-log-page',
  standalone: true,
  imports: [AsyncStateWrapperComponent, StaffNavComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './audit-log.page.html',
})
export class AuditLogPage implements OnInit {
  private readonly auditLogApi = inject(AuditLogApiService);

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly page = signal<AuditLogPageResponse | null>(null);
  readonly appointmentIdFilter = signal('');
  readonly fromFilter = signal('');
  readonly toFilter = signal('');

  ngOnInit(): void {
    this.load();
  }

  onAppointmentIdFilterChange(value: string): void {
    this.appointmentIdFilter.set(value);
    this.load(0);
  }

  onFromFilterChange(value: string): void {
    this.fromFilter.set(value);
    this.load(0);
  }

  onToFilterChange(value: string): void {
    this.toFilter.set(value);
    this.load(0);
  }

  goToPage(page: number): void {
    this.load(page);
  }

  private load(page = 0): void {
    this.loading.set(true);
    this.error.set(null);
    const appointmentId = this.appointmentIdFilter() ? Number(this.appointmentIdFilter()) : undefined;
    // §8.18's from/to are full timestamps (unlike §8.9's date-only from/to) — the date inputs
    // are converted to the start of that clinic-local calendar day; `to` is exclusive of the
    // next day, matching the same from/to semantics already used by the appointments list.
    const from = this.fromFilter() ? `${this.fromFilter()}T00:00:00Z` : undefined;
    const to = this.toFilter() ? `${addOneDay(this.toFilter())}T00:00:00Z` : undefined;
    this.auditLogApi.list({ appointmentId, from, to, page }).subscribe({
      next: (result) => {
        this.loading.set(false);
        this.page.set(result);
      },
      error: (err: AppHttpError) => {
        this.loading.set(false);
        this.error.set(err.userMessage);
      },
    });
  }
}

/** yyyy-MM-dd (as produced by a native date input) plus one calendar day. */
function addOneDay(isoDate: string): string {
  const date = new Date(`${isoDate}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + 1);
  return date.toISOString().slice(0, 10);
}
