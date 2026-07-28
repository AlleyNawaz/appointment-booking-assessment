import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { StaffSessionService } from './auth/staff-session.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';

/**
 * Staff-console nav (PRD §4.1) — hides/shows items per the role-visibility
 * matrix. UX convenience only: every underlying endpoint independently
 * re-checks authorization server-side (§10) regardless of what this hides.
 */
@Component({
  selector: 'app-staff-nav',
  standalone: true,
  imports: [RouterLink, TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav>
      <a routerLink="/staff/appointments">{{ 'staff.nav.appointments' | translate }}</a>
      <a routerLink="/staff/availability/hours">{{ 'staff.nav.hours' | translate }}</a>
      <a routerLink="/staff/availability/unavailability">{{ 'staff.nav.timeOff' | translate }}</a>
      <a routerLink="/staff/availability/holidays">{{ 'staff.nav.holidays' | translate }}</a>
      @if (showAdmin()) {
        <a routerLink="/staff/admin/appointment-types">{{ 'staff.nav.appointmentTypes' | translate }}</a>
        <a routerLink="/staff/admin/providers">{{ 'staff.nav.providers' | translate }}</a>
        <a routerLink="/staff/admin/settings">{{ 'staff.nav.systemSettings' | translate }}</a>
      }
      @if (showAuditLog()) {
        <a routerLink="/staff/audit-log">{{ 'staff.nav.auditLog' | translate }}</a>
      }
    </nav>
  `,
})
export class StaffNavComponent {
  private readonly staffSession = inject(StaffSessionService);

  /** §4.1: Admin nav items are hidden entirely for ROLE_STAFF and ROLE_PROVIDER. */
  readonly showAdmin = computed(() => {
    const role = this.staffSession.role();
    return role === 'ROLE_ADMIN' || role === 'ROLE_SYSADMIN';
  });

  /** §4.1: Audit Log is hidden for every role except ROLE_SYSADMIN. */
  readonly showAuditLog = computed(() => this.staffSession.role() === 'ROLE_SYSADMIN');
}
