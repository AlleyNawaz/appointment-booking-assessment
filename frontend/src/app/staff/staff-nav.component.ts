import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { StaffSessionService } from './auth/staff-session.service';

/**
 * Staff-console nav (PRD §4.1) — hides/shows items per the role-visibility
 * matrix. UX convenience only: every underlying endpoint independently
 * re-checks authorization server-side (§10) regardless of what this hides.
 */
@Component({
  selector: 'app-staff-nav',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav>
      <a routerLink="/staff/appointments">Appointments</a>
      <a routerLink="/staff/availability/hours">Hours</a>
      <a routerLink="/staff/availability/unavailability">Time Off</a>
      <a routerLink="/staff/availability/holidays">Holidays</a>
      @if (showAdmin()) {
        <a routerLink="/staff/admin/appointment-types">Appointment Types</a>
        <a routerLink="/staff/admin/providers">Providers</a>
        <a routerLink="/staff/admin/settings">System Settings</a>
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
}
